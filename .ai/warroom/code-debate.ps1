# ============================================================
# code-debate.ps1 — 双 AI 批判-修复循环协调脚本 v2
#
# 核心机制: 两个 AI 工具（claude / agy）各自调用本脚本，脚本负责"排队、追加、判断收敛、推进回合"。
#           同一时刻只有一个工具在写；未轮到你时 wait 有界等待（-TimeoutSeconds，默认 90s）。
#           收敛协议（v2 多轮来回批判式评价，不再单方一言定胜负）:
#             - 单方写「无剩余分歧/无新反对/已统一/无歧义」只表示"本回合无新反对"，不等于结束；
#             - 总轮数须 >= -MinRounds（默认 4，双方各至少 2 回合）后才允许进入收敛流程；
#             - 收敛需双方在相邻两回合连续各自声明一次：一方声明 → 交对方复核确认 → 对方也声明才结束；
#             - 任一回合出现新批判（写「尚未收敛」）会清除挂起的确认请求，辩论继续；
#             - -MaxRounds（默认 8）是兜底上限，到顶未收敛则强制结束并标注需人工确认。
#
# 与 warroom.ps1 的区别:
#   - 没有阶段(s0..s6)七站流水线，只有一个"批判-修复"循环
#   - 每个回合的模型同时做两件事: 批判式评价 + 直接修改代码（不再只是写 Markdown 草稿）
#   - 所有改动仍须基于项目 AI.md 总纲（路径发现/功能原型/强阻断/核心原则），并遵守 .ai/guard.md / workflow.md / agents.md
#
# 使用方式（在已登录 claude / agy 的 PowerShell 中）:
#   ① 把需求写入 .ai/warroom/req-active/request.md（白话即可，脚本第一次被调用时自动初始化）
#   ② 开两个 PowerShell 窗口（都停在项目根目录），分别启动 claude / agy，各粘贴下面对应的启动指令。
#
#   【窗口 1 · claude】: 启动 `claude` 后粘贴:
#     你是 code-debate 辩论中的 claude。需求已写在 D:\ideaprojects\pap4j-boot3\.ai\warroom\req-active\request.md。
#     循环执行以下步骤，直到脚本输出"✅ 辩论结束"：
#       1. 运行: powershell -ExecutionPolicy Bypass -File D:\ideaprojects\pap4j-boot3\.ai\warroom\code-debate.ps1 -Action wait -Tool claude
#       2. 若输出含"未轮到你": 回到步骤 1（有界等待，没轮到你时返回提示，重试即可）。
#       3. 若输出含"轮到你": 按简报做批判式评价并直接修改代码（遵守 AI.md 的 [Search]→[Plan]/[QuickPlan]→[Edit]→[Shell] 流程与链式验证），
#          然后把你这一回合的「批判意见 + 改动说明 + 验证结果」(Markdown) 覆盖写入
#          D:\ideaprojects\pap4j-boot3\.ai\warroom\req-active\debate-claude.draft.md（不要改其它任何文件）。
#       4. 运行: powershell -ExecutionPolicy Bypass -File D:\ideaprojects\pap4j-boot3\.ai\warroom\code-debate.ps1 -Action commit -Tool claude
#       5. 回到步骤 1。
#
#   【窗口 2 · agy】: 启动 `agy` 后粘贴:
#     你是 code-debate 辩论中的 agy。需求已写在 D:\ideaprojects\pap4j-boot3\.ai\warroom\req-active\request.md。
#     循环执行以下步骤，直到脚本输出"✅ 辩论结束"：
#       1. 运行: powershell -ExecutionPolicy Bypass -File D:\ideaprojects\pap4j-boot3\.ai\warroom\code-debate.ps1 -Action wait -Tool agy
#       2. 若输出含"未轮到你": 回到步骤 1（有界等待，没轮到你时返回提示，重试即可）。
#       3. 若输出含"轮到你": 按简报做批判式评价并直接修改代码（遵守 AI.md 的 [Search]→[Plan]/[QuickPlan]→[Edit]→[Shell] 流程与链式验证），
#          然后把你这一回合的「批判意见 + 改动说明 + 验证结果」(Markdown) 覆盖写入
#          D:\ideaprojects\pap4j-boot3\.ai\warroom\req-active\debate-agy.draft.md（不要改其它任何文件）。
#       4. 运行: powershell -ExecutionPolicy Bypass -File D:\ideaprojects\pap4j-boot3\.ai\warroom\code-debate.ps1 -Action commit -Tool agy
#       5. 回到步骤 1。
#
#   其它:
#     status           查看当前状态
#     -ReqName         需求目录名（默认 active，与 warroom.ps1 共用 request.md）
#     -MinRounds       最少轮数（默认 4；达到后才允许收敛，用于强制多轮来回批判式评价）
#     -MaxRounds       回合上限（默认 8；到顶未收敛则强制结束并标注需人工确认）
#     -TimeoutSeconds  wait 单次等待上限（默认 90，返回"未轮到你"后重试即可）
# ============================================================
param(
  [ValidateSet('init','status','wait','commit')][string]$Action = 'status',
  [ValidateSet('claude','agy')][string]$Tool,
  [Alias('r')][string]$ReqName = 'active',
  [int]$MinRounds = 4,
  [int]$MaxRounds = 8,
  [int]$TimeoutSeconds = 90
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Prefix = '[Code-Debate]'
# 兜底：上限不得低于最少轮数，否则协议无法走完
if ($MaxRounds -lt $MinRounds) { $MaxRounds = $MinRounds + 2 }
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$ScriptRoot = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
$ReqDir = Join-Path $ScriptRoot "req-$ReqName"
$StateFile = Join-Path $ReqDir 'debate-state.json'
$DraftFile = Join-Path $ReqDir "debate-$Tool.draft.md"
$ConvergeRe = '无剩余分歧|无新反对|已统一|无歧义'

# ---------- 工具函数 ----------
function Write-Utf8 { param($Path,$Content) [IO.File]::WriteAllText($Path,$Content,$Utf8NoBom) }
function Read-Utf8 { param($Path) if (Test-Path $Path) { [IO.File]::ReadAllText($Path) } else { '' } }

function Get-State {
  if (-not (Test-Path $StateFile)) { return $null }
  (Read-Utf8 $StateFile) | ConvertFrom-Json
}
function Set-State { param($s) Write-Utf8 $StateFile ($s | ConvertTo-Json -Compress) }

# 提取 draft 中「【是否收敛】」小节之后的收敛标记行（通常只有一行：无剩余分歧 / 尚未收敛）。
# 只解析标记小节，绝不全文子串匹配——否则正文引用对方「无剩余分歧」字样，
# 会把「尚未收敛」误判成收敛声明（v3 修复，见 commit 收敛判定）。
function Get-ConvergenceMarker {
  param($content)
  $lines = @($content -split "`r?`n")
  $markerIdx = -1
  for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '【是否收敛】') { $markerIdx = $i }
  }
  if ($markerIdx -lt 0) { return '' }
  $tail = @()
  for ($i = $markerIdx + 1; $i -lt $lines.Count; $i++) {
    if ($lines[$i].Trim() -ne '') { $tail += $lines[$i].Trim() }
  }
  return ($tail -join ' ')
}

# 自动初始化：state 缺失但 request.md 存在且非空时，自动建状态并播种第一回合
function Ensure-Init {
  $s = Get-State
  if ($s) { return $s }
  if (-not (Test-Path (Join-Path $ReqDir 'request.md'))) {
    Write-Host "$Prefix 缺少需求：请先写 $ReqDir\request.md"
    exit 1
  }
  $req = (Read-Utf8 (Join-Path $ReqDir 'request.md')).Trim()
  if (-not $req) {
    Write-Host "$Prefix 需求为空：请在 $ReqDir\request.md 写入需求内容"
    exit 1
  }
  New-Item -ItemType Directory -Force -Path $ReqDir | Out-Null
  $rec = Join-Path $ReqDir 'debate.md'
  if (-not (Test-Path $rec)) {
    Write-Utf8 $rec "<!-- code-debate 开始 -->`n## [辩论] 需求已就绪，等待 claude 第一轮。`n`n"
  }
  $s = [pscustomobject]@{ round=1; turn='claude'; turnNo=0; done=$false; convergeProposal=$false; proposer=$null }
  Set-State $s
  Write-Host "$Prefix 已自动初始化（需求来自 request.md），轮到 $($s.turn)"
  return $s
}

# ---------- git 差异摘要（排除 .ai/warroom 自身产物） ----------
function Get-GitSummary {
  try {
    $root = git rev-parse --show-toplevel 2>$null
    if (-not $root) { return @('（未检测到 git 仓库，跳过差异摘要）') }
    $porcelain = git -C $root status --porcelain -- . ':(exclude).ai/warroom/**' 2>$null
    $changed = @($porcelain)
    $lines = @()
    $lines += "仓库根: $root"
    $lines += "未提交改动文件数(排除 .ai/warroom): $($changed.Count)"
    if ($changed.Count -gt 0) {
      $lines += "----- git diff --stat -----"
      $stat = git -C $root diff --stat -- . ':(exclude).ai/warroom/**' 2>$null
      $lines += @($stat) | Select-Object -First 25
      $lines += "（完整差异请用 git diff 自行查看）"
    } else {
      $lines += "（工作区相对干净，无未提交代码改动）"
    }
    return $lines
  } catch {
    return @('（获取 git 信息失败）')
  }
}

# ---------- 每回合任务提示 ----------
function Get-TaskText {
  return @'
按 AI.md 总纲执行本轮「批判式评价 + 修复」：
1. 阅读需求（上方 request.md）。第 1 轮必须先通读 D:\ideaprojects\pap4j-boot3\AI.md 总纲
   （路径发现/功能原型/强阻断/核心原则），并按需查阅 .ai/guard.md、.ai/workflow.md、.ai/agents.md。
2. 查看当前未提交改动（git status / git diff，已自动排除 .ai/warroom 目录）与辩论记录 debate.md 尾部。
3. 批判式评价: 对照需求逐条核对当前实现，找出偏差/缺失/边界与并发漏洞/测试缺口/违反 guard.md 之处。
   —— 这是对抗式审查：必须逐条给出具体问题与证据（可复现的失败场景），禁止写「整体没问题/已完备」式橡皮图章结论；
      即便一时找不出问题，也要复核对方论证链中的关键假设，试图证伪。
4. 若有问题: 直接修改代码。遵守 AI.md 的 [Search]→[Plan]/[QuickPlan]→[Edit]→[Shell] 流程:
   - 定位文件用 [Search]，禁止凭包名/直觉猜测路径；
   - 触发强阻断（跨模块调用协议、≥3 个 src/main 文件联动、公共 API 签名变更等）必须先给 [Plan]；
   - 每次 [Edit] 后必须链式跟随 [Shell] 验证（编译/相关测试），按 workflow.md 结果闭环；
   - 测试优先用 ./.agent/agent-test.cmd，或 mvn 且带 "-Dfile.encoding=UTF-8" "-Dmaven.gitcommitid.skip=true"；
   - 外科手术式修改，禁止顺带重构无关老代码；同一 [Edit]→[Shell] 验证循环最多 2 轮，超限停下汇报。
5. 严禁 git commit / git push（AI.md 红线），改动由用户审阅后提交。
6. 收敛协议（重要，勿误解）：
   - 写「无剩余分歧」只表示"本回合你无新反对"，不会立即结束辩论；
   - 需要 总轮数 >= MinRounds（默认 4）且 双方在相邻回合各自连续声明一次 才会结束（脚本会明确提示你进入复核确认回合）；
   - 因此请在你确实审阅并认可当前实现时，再在回复结尾独立一行写「无剩余分歧」；只要还有任何新反对，就写「尚未收敛」。
7. 把以下内容（Markdown）写入你的 draft 文件:
   - 【批判意见】本轮发现的问题（逐条）
   - 【改动说明】改了哪些文件/方法、为什么（若无改动写"无"）
   - 【验证结果】跑了什么命令、结果如何
   - 【是否收敛】结尾独立一行: 无剩余分歧 或 尚未收敛
'@
}

# ---------- 文件协议 ----------
function New-TurnEntry {
  param($tool,$round,$turnNo,$body)
  $stamp = Get-Date -Format 'yyyy-MM-dd HH:mm'
  $sb = New-Object System.Text.StringBuilder
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("<!-- TURN-$turnNo -->")
  [void]$sb.AppendLine("## [$tool · R$round · TURN-$turnNo] $stamp")
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine($body)
  [void]$sb.AppendLine("")
  return $sb.ToString()
}

function Build-Conclusion {
  param($s,$content,[string]$note)
  $sb = New-Object System.Text.StringBuilder
  [void]$sb.AppendLine("> 需求: request.md")
  [void]$sb.AppendLine("> 状态: ✅ 已收敛（round $($s.round)）$(if ($note) { ' ｜ ' + $note } else { '' })")
  [void]$sb.AppendLine("> 完成回合: $($s.turnNo) 次")
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("# 辩论统一结论")
  [void]$sb.AppendLine($content)
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("> 完整辩论记录: debate.md")
  return $sb.ToString()
}

# ---------- 简报 ----------
function Write-Briefing {
  param($s)
  Write-Host ""
  Write-Host "===== code-debate 回合协调 ====="
  Write-Host "第 $($s.round) 轮 | 轮到你: $($s.turn)"
  Write-Host "角色: 批判式评价 + 修复者（直接改代码）"
  if ($s.convergeProposal) {
    Write-Host "⚠ 复核确认回合：$($s.proposer) 上一回合已声明「无剩余分歧」。"
    Write-Host "  若你亦无新反对，请回复「无剩余分歧」以结束辩论；若你发现新问题，请提出批判并修改（会继续辩论）。"
  }
  Write-Host "----- 需求 request.md -----"
  Write-Host (Read-Utf8 (Join-Path $ReqDir 'request.md'))
  Write-Host "----- 当前未提交改动 -----"
  Get-GitSummary | ForEach-Object { Write-Host $_ }
  Write-Host "----- 辩论记录 debate.md 尾部 -----"
  $rec = Join-Path $ReqDir 'debate.md'
  if (Test-Path $rec) { Get-Content $rec -Encoding UTF8 | Select-Object -Last 40 } else { Write-Host "（无）" }
  Write-Host "----- 你的任务 -----"
  Write-Host (Get-TaskText)
  Write-Host "----- 规则 -----"
  Write-Host "1. 只把你的发言正文覆盖写入: $DraftFile（Markdown）"
  Write-Host "2. 不要修改除 $DraftFile 之外的任何文件"
  Write-Host "3. 写完后运行:"
  Write-Host "   powershell -ExecutionPolicy Bypass -File $(Join-Path $ScriptRoot 'code-debate.ps1') -Action commit -Tool $($s.turn)"
}

# ---------- 动作 ----------
if ($Action -eq 'init') {
  New-Item -ItemType Directory -Force -Path $ReqDir | Out-Null
  if (-not (Test-Path (Join-Path $ReqDir 'request.md'))) {
    Write-Host "$Prefix 缺少需求：请先写 $ReqDir\request.md"; exit 1
  }
  $s = [pscustomobject]@{ round=1; turn='claude'; turnNo=0; done=$false; convergeProposal=$false; proposer=$null }
  Set-State $s
  Write-Host "$Prefix 已重置状态: $ReqDir（轮到 claude）"
  $rec = Join-Path $ReqDir 'debate.md'
  if (Test-Path $rec) {
    Write-Host "$Prefix 提示: 旧辩论记录 debate.md 仍在，如需从头开始可删除该文件。"
  } else {
    Write-Utf8 $rec "<!-- code-debate 初始化 -->`n## [辩论] 需求已就绪，等待 claude 第一轮。`n`n"
  }
  exit 0
}

if ($Action -eq 'status') {
  $s = Ensure-Init
  $confirmInfo = if ($s.convergeProposal) { " | 待确认: $($s.proposer) 已声明收敛，等 $($s.turn) 复核" } else { '' }
  Write-Host "$Prefix 轮次=$($s.round) | 当前轮到=$($s.turn) | 已完成回合=$($s.turnNo) | done=$($s.done)$confirmInfo"
  $rec = Join-Path $ReqDir 'debate.md'
  Write-Host "----- debate.md 尾部 -----"
  if (Test-Path $rec) { Get-Content $rec -Encoding UTF8 | Select-Object -Last 15 } else { Write-Host "（无）" }
  exit 0
}

if ($Action -eq 'wait') {
  if (-not $Tool) { Write-Host "$Prefix 需要 -Tool claude|agy"; exit 1 }
  $s = Ensure-Init
  if ($s.done) { Write-Host "$Prefix ✅ 辩论结束"; exit 0 }
  $waited = 0
  while ((Get-State).turn -ne $Tool) {
    $s = Get-State
    if ($s.done) { Write-Host "$Prefix ✅ 辩论结束"; exit 0 }
    if ($waited -ge $TimeoutSeconds) {
      Write-Host "$Prefix 未轮到你（已等待 ${waited}s）。请重试 wait 继续等待（当前轮到 $($s.turn)）"
      exit 0
    }
    Start-Sleep -Seconds 3
    $waited = $waited + 3
  }
  Write-Briefing (Get-State)
  exit 0
}

if ($Action -eq 'commit') {
  if (-not $Tool) { Write-Host "$Prefix 需要 -Tool claude|agy"; exit 1 }
  $s = Ensure-Init
  if ($s.done) { Write-Host "$Prefix ✅ 辩论结束"; exit 0 }
  if ($s.turn -ne $Tool) { Write-Host "$Prefix 还没轮到你（当前轮到 $($s.turn)）"; exit 1 }
  if (-not (Test-Path $DraftFile)) { Write-Host "$Prefix 找不到 $DraftFile，请先写你的发言"; exit 1 }

  $content = (Read-Utf8 $DraftFile).Trim()
  if (-not $content) { Write-Host "$Prefix 发言为空"; Remove-Item $DraftFile -Force; exit 1 }

  $s.turnNo = $s.turnNo + 1
  $rec = Join-Path $ReqDir 'debate.md'
  Add-Content -Path $rec -Encoding UTF8 -Value (New-TurnEntry $Tool $s.round $s.turnNo $content)
  Remove-Item $DraftFile -Force
  Write-Host "$Prefix $Tool · R$($s.round) · TURN-$($s.turnNo) 已追加到 debate.md"

  # ---------- 收敛判定（多轮来回 + 双方确认，不再单方一言定胜负） ----------
  # 规则：
  #   1) 单方「无剩余分歧」≠ 结束：总轮数须 >= MinRounds，且双方在相邻回合各自连续声明一次
  #      （一方声明 → 挂起确认请求 → 交对方复核 → 对方也声明）才真正结束；
  #   2) MinRounds 之前的「无剩余分歧」只作记录，继续辩论，不进入确认流程；
  #   3) 任一方提出新批判（尚未收敛）会清除挂起的确认请求，辩论继续；
  #   4) MaxRounds 是兜底上限，到顶未收敛则强制结束并标注需人工确认。
  # v3 修复：只解析 draft 结尾「【是否收敛】」标记行，不再对全文做子串匹配。
  # （否则正文引用对方「无剩余分歧」字样会把"尚未收敛"误判为收敛声明，导致辩论被提前/错误结束。）
  $declared = ((Get-ConvergenceMarker $content) -match $ConvergeRe)
  $other    = if ($Tool -eq 'claude') { 'agy' } else { 'claude' }

  if ($declared) {
    if ($s.round -lt $MinRounds) {
      # 未达最少轮数：记录但继续，不进入确认流程
      $s.turn = $other; $s.round = $s.round + 1
      Set-State $s
      Write-Host "$Prefix $Tool 已声明「无剩余分歧」，但未达最少轮数($MinRounds)，继续辩论。轮到 $other（第 $($s.round) 轮）。"
      exit 0
    }
    if ($s.convergeProposal -and $s.proposer -ne $Tool) {
      # 对方上一回合已声明，本回合另一方也声明 → 双方连续确认，结束
      Write-Utf8 (Join-Path $ReqDir 'debate-conclusion.md') (Build-Conclusion $s $content "双方确认无剩余分歧")
      $s.done = $true
      Set-State $s
      Write-Host "$Prefix ✅ 辩论结束（第 $($s.round) 轮，双方确认无剩余分歧）。结论见 debate-conclusion.md"
      exit 0
    }
    # 首次（或重提）收敛声明：挂起确认请求，交对方复核确认
    $s.convergeProposal = $true
    $s.proposer = $Tool
    $s.turn = $other; $s.round = $s.round + 1
    Set-State $s
    Write-Host "$Prefix $Tool 已声明「无剩余分歧」，交由 $other 复核确认；$other 若亦无新反对（回复「无剩余分歧」）则结束，若有新反对则继续。轮到 $other（第 $($s.round) 轮）。"
    exit 0
  }

  # 未声明收敛（提出新批判）：清除挂起的确认请求
  $s.convergeProposal = $false
  $s.proposer = $null

  if ($s.round -ge $MaxRounds) {
    Write-Utf8 (Join-Path $ReqDir 'debate-conclusion.md') (Build-Conclusion $s $content "达到回合上限($MaxRounds)，需人工确认")
    $s.done = $true
    Set-State $s
    Write-Host "$Prefix 达到回合上限，强制结束，需人工确认。"
    exit 0
  }

  # 未收敛：移交另一个模型
  $s.turn = $other
  $s.round = $s.round + 1
  Set-State $s
  Write-Host "$Prefix 已记录，轮到 $other（第 $($s.round) 轮）。未轮到你时 wait 会阻塞等待。"
  exit 0
}
