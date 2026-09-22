# ============================================================
# code-debate.ps1 — 双 AI 批判-修复循环通用协调脚本 v3
#
# 核心机制: 两个 AI 工具各自调用本脚本，脚本负责"排队、追加、判断收敛、推进回合"。
#           同一时刻只有一个工具在写；未轮到你时 wait 有界等待（-TimeoutSeconds，默认 90s）。
#           收敛协议（多轮来回批判式评价，不再单方一言定胜负）:
#             - 单方写「无剩余分歧/无新反对/已统一/无歧义」只表示"本回合无新反对"，不等于结束；
#             - 总轮数须 >= -MinRounds（默认 4，双方各至少 2 回合）后才允许进入收敛流程；
#             - 收敛需双方在相邻两回合连续各自声明一次：一方声明 → 交对方复核确认 → 对方也声明才结束；
#             - 任一回合出现新批判（写「尚未收敛」）会清除挂起的确认请求，辩论继续；
#             - -MaxRounds（默认 8）是兜底上限，到顶未收敛则强制结束并标注需人工确认。
#
# 特点与适用性:
#   - 零手动初始化：直接通过自然语言让第一个 AI 自动带需求启动，无需手动建目录或跑 init。
#   - 通用可移植：可以在任意位置、任意代码仓库运行，自动定位当前项目根目录与需求目录。
#   - 工具与技术栈解耦：默认 claude 与 agy（亦可通过 -ToolA / -ToolB 切换为任意模型工具）。
#
# ------------------------------------------------------------
# 极简使用指南（无需任何手动前置准备，开窗口直接发自然语言）：
#
# 【窗口 1 · claude】：打开项目终端启动 claude，直接发送以下自然语言：
# ------------------------------------------------------------
# 我们本次的需求是：【在此直接用白话写你的任意需求】
#
# 请作为 claude 参与当前项目的双 AI 批判审查与修复循环（code-debate）：
# 循环执行以下步骤，直到脚本输出"✅ 辩论结束"：
# 1. 运行: powershell -ExecutionPolicy Bypass -File .ai/warroom/code-debate.ps1 -Action wait -Tool claude -req "本次需求内容"
# 2. 若输出含"未轮到你": 稍等片刻重新运行步骤 1。
# 3. 若输出含"轮到你": 按简报进行对抗审查并直接修改代码跑测试验证；然后将你的批判意见、改动说明、测试结果覆盖写入简报中指定的 draft 草稿文件。
# 4. 运行: powershell -ExecutionPolicy Bypass -File .ai/warroom/code-debate.ps1 -Action commit -Tool claude
# 5. 回到步骤 1。
#
# 【窗口 2 · agy】：打开项目终端启动 agy，直接发送以下自然语言：
# ------------------------------------------------------------
# 请作为 agy 参与当前项目正在进行的双 AI 批判审查与修复循环（code-debate）：
# 循环执行以下步骤，直到脚本输出"✅ 辩论结束"：
# 1. 运行: powershell -ExecutionPolicy Bypass -File .ai/warroom/code-debate.ps1 -Action wait -Tool agy
# 2. 若输出含"未轮到你": 稍等片刻重新运行步骤 1。
# 3. 若输出含"轮到你": 按简报进行对抗审查并直接修改代码跑测试验证；然后将你的批判意见、改动说明、测试结果覆盖写入简报中指定的 draft 草稿文件。
# 4. 运行: powershell -ExecutionPolicy Bypass -File .ai/warroom/code-debate.ps1 -Action commit -Tool agy
# 5. 回到步骤 1。
# ============================================================
param(
  [ValidateSet('init','status','wait','commit')][string]$Action = 'status',
  [string]$Tool,
  [string]$ToolA = 'claude',
  [string]$ToolB = 'agy',
  [string]$Workspace = '',
  [Alias('req')][string]$Requirement = '',
  [Alias('r')][string]$ReqName = 'active',
  [int]$MinRounds = 4,
  [int]$MaxRounds = 8,
  [int]$TimeoutSeconds = 90
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Prefix = '[Code-Debate]'
if ($MaxRounds -lt $MinRounds) { $MaxRounds = $MinRounds + 2 }
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)

# ---------- 路径与项目动态解析 ----------
# 1. 脚本自身绝对路径
$ScriptPath = if ($PSCommandPath) {
  $PSCommandPath
} elseif ($MyInvocation.MyCommand.Path) {
  $MyInvocation.MyCommand.Path
} else {
  (Join-Path (Get-Location).Path 'code-debate.ps1')
}
$ScriptRoot = Split-Path -Parent $ScriptPath

# 2. 动态定位目标项目根目录 (ProjectRoot)
$ProjectRoot = if ($Workspace -and (Test-Path $Workspace)) {
  (Resolve-Path $Workspace).Path
} else {
  $gitRoot = try { git rev-parse --show-toplevel 2>$null } catch { $null }
  if ($gitRoot -and (Test-Path $gitRoot)) {
    (Resolve-Path $gitRoot).Path
  } else {
    (Get-Location).Path
  }
}

# 3. 动态定位需求与工作目录 (ReqDir)
$ReqDir = if (Test-Path (Join-Path $ScriptRoot "req-$ReqName")) {
  Join-Path $ScriptRoot "req-$ReqName"
} elseif (Test-Path (Join-Path $ProjectRoot ".ai\warroom")) {
  Join-Path $ProjectRoot ".ai\warroom\req-$ReqName"
} else {
  if ($ScriptRoot.ToLower().EndsWith('warroom')) {
    Join-Path $ScriptRoot "req-$ReqName"
  } else {
    Join-Path $ProjectRoot ".ai\warroom\req-$ReqName"
  }
}

$StateFile = Join-Path $ReqDir 'debate-state.json'
$DraftFile = if ($Tool) { Join-Path $ReqDir "debate-$Tool.draft.md" } else { '' }
$ConvergeRe = '无剩余分歧|无新反对|已统一|无歧义|一致认可'

# ---------- 工具函数 ----------
function Write-Utf8 { param($Path,$Content) [IO.File]::WriteAllText($Path,$Content,$Utf8NoBom) }
function Read-Utf8 { param($Path) if (Test-Path $Path) { [IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8) } else { '' } }

function Get-State {
  if (-not (Test-Path $StateFile)) { return $null }
  try {
    (Read-Utf8 $StateFile) | ConvertFrom-Json
  } catch {
    return $null
  }
}

function Set-State {
  param($s)
  Write-Utf8 $StateFile ($s | ConvertTo-Json -Compress)
}

# 提取 draft 中「【是否收敛】」小节之后的收敛标记行
function Get-ConvergenceMarker {
  param($content)
  $lines = @($content -split "`r?`n")
  $markerIdx = -1
  for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '【是否收敛】|【收敛判断】|【收敛状态】|是否收敛') {
      $markerIdx = $i
    }
  }
  if ($markerIdx -lt 0) { return '' }
  $tail = @()
  for ($i = $markerIdx + 1; $i -lt $lines.Count; $i++) {
    if ($lines[$i].Trim() -ne '') { $tail += $lines[$i].Trim() }
  }
  return ($tail -join ' ')
}

# 自动初始化：state 缺失时根据 request.md 初始化状态并排期第一回合
function Ensure-Init {
  $reqFile = Join-Path $ReqDir 'request.md'
  if ($Requirement -and $Requirement.Trim()) {
    New-Item -ItemType Directory -Force -Path $ReqDir | Out-Null
    Write-Utf8 $reqFile $Requirement.Trim()
  }

  $s = Get-State
  if ($s) {
    if (-not $s.toolA) { $s | Add-Member -NotePropertyName toolA -NotePropertyValue $ToolA -ErrorAction SilentlyContinue }
    if (-not $s.toolB) { $s | Add-Member -NotePropertyName toolB -NotePropertyValue $ToolB -ErrorAction SilentlyContinue }
    return $s
  }

  if (-not (Test-Path $reqFile)) {
    New-Item -ItemType Directory -Force -Path $ReqDir | Out-Null
    $initTemplate = "# 需求说明`n`n请在此写入本轮对抗辩论与修复的具体需求内容（白话描述或技术要点皆可）..."
    Write-Utf8 $reqFile $initTemplate
    Write-Host "$Prefix 缺少需求：已自动创建需求模板文件："
    Write-Host "  -> $reqFile"
    Write-Host "$Prefix 提示: 你可以直接在命令后加 -req `"你的需求内容`" 免去手动编辑直接启动。"
    exit 1
  }

  $req = (Read-Utf8 $reqFile).Trim()
  if (-not $req -or $req -match '^# 需求说明\s+请在此写入本轮对抗辩论') {
    Write-Host "$Prefix 需求内容为空。请在 $reqFile 写入需求，或在命令后加 -req `"你的需求`"。"
    exit 1
  }

  New-Item -ItemType Directory -Force -Path $ReqDir | Out-Null
  $rec = Join-Path $ReqDir 'debate.md'
  if (-not (Test-Path $rec)) {
    Write-Utf8 $rec "<!-- code-debate 开始 -->`n## [辩论] 需求已就绪，等待 $ToolA 第一轮。`n`n"
  }

  $s = [pscustomobject]@{
    round            = 1
    turn             = $ToolA
    turnNo           = 0
    done             = $false
    convergeProposal = $false
    proposer         = $null
    toolA            = $ToolA
    toolB            = $ToolB
  }
  Set-State $s
  Write-Host "$Prefix 已自动初始化（需求目录: $ReqDir），首轮轮到 $($s.turn)"
  return $s
}

# ---------- git 差异摘要 ----------
function Get-GitSummary {
  try {
    $root = git -C $ProjectRoot rev-parse --show-toplevel 2>$null
    if (-not $root) { return @('（未检测到 git 仓库，跳过版本差异摘要）') }
    $porcelain = git -C $root status --porcelain -- . ':(exclude).ai/warroom/**' ':(exclude)*debate*' 2>$null
    $changed = @($porcelain)
    $lines = @()
    $lines += "仓库根目录: $root"
    $lines += "未提交改动文件数(排除辩论状态文件): $($changed.Count)"
    if ($changed.Count -gt 0) {
      $lines += "----- git diff --stat -----"
      $stat = git -C $root diff --stat -- . ':(exclude).ai/warroom/**' ':(exclude)*debate*' 2>$null
      $lines += @($stat) | Select-Object -First 25
      $lines += "（完整改动可直接运行 git diff 自行核对）"
    } else {
      $lines += "（工作区干净，无未提交代码改动）"
    }
    return $lines
  } catch {
    return @('（获取 git 状态失败）')
  }
}

# ---------- 每回合任务提示 ----------
function Get-TaskText {
  $aiMdPath = Join-Path $ProjectRoot 'AI.md'
  $hasAiMd = Test-Path $aiMdPath
  $ruleTip = if ($hasAiMd) {
    "第 1 轮请先通读 $aiMdPath 项目总纲及相关设计规范。"
  } else {
    "请遵循当前项目既有的架构规范、编码约定及目录结构组织代码。"
  }

  return @"
按规范执行本轮「批判式对抗审查 + 修复落盘」：
1. 阅读需求（上方 request.md 内容）。$ruleTip
2. 查看当前未提交改动（git status / git diff）与辩论记录 debate.md 尾部最新发言。
3. 批判式对抗审查:
   - 对照需求逐条核验当前代码实现，寻找逻辑偏差、边缘缺漏、并发安全隐患、测试遗漏或缺陷；
   - 必须逐条给出具体可复现的场景或逻辑漏洞推演，禁止发表泛泛的「实现完整/无问题」等套话；
   - 若认可对方改动，也须主动对关键假定做极限边界测试并试图证伪。
4. 落地修复（发现问题立即动手改代码）:
   - 精准定位：通过真实文件搜索定位目标，切忌凭记忆捏造文件路径；
   - 闭环验证：改动后必须链式执行本地测试验证（如项目内置测试脚本、mvn test、npm test、pytest、go test 等）；
   - 外科手术式改动：只修复本需求相关代码，严禁顺带改动无关历史代码。
5. 安全红线: 严禁自动执行未经用户审阅的 git push / git commit。
6. 收敛判定协议:
   - 独立一行写「无剩余分歧」仅代表你本回合无新反驳，并不意味着辩论立刻结束；
   - 协议要求：总轮数 >= MinRounds（当前设定 $MinRounds 轮），且双方在相邻回合各自连续明确声明一次「无剩余分歧」才会最终收敛；
   - 只要本轮你还发现任何瑕疵、缺陷或异议，结尾请写「尚未收敛」。
7. 把以下内容覆盖写入你的 draft 文件:
   - 【批判意见】本轮针对代码或方案指出的具体问题（逐条）
   - 【改动说明】本次实际修改了哪些文件/方法、改动逻辑（若无改动写"无"）
   - 【验证结果】执行了什么测试或验证命令、输出与退出码如何
   - 【是否收敛】结尾独立一行写：无剩余分歧 或 尚未收敛
"@
}

# ---------- 文件协议格式化 ----------
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
  [void]$sb.AppendLine("> 需求目录: $ReqDir")
  [void]$sb.AppendLine("> 状态: ✅ 已收敛（round $($s.round)）$(if ($note) { ' ｜ ' + $note } else { '' })")
  [void]$sb.AppendLine("> 完成回合: $($s.turnNo) 次")
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("# 辩论统一结论")
  [void]$sb.AppendLine($content)
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("> 完整辩论轨迹请查看: debate.md")
  return $sb.ToString()
}

# ---------- 简报生成 ----------
function Write-Briefing {
  param($s)
  Write-Host ""
  Write-Host "===== code-debate 回合协调 ====="
  Write-Host "项目根目录: $ProjectRoot"
  Write-Host "需求目录:   $ReqDir"
  Write-Host "第 $($s.round) 轮 | 轮到你: $($s.turn)"
  Write-Host "角色定位: 批判式对抗评价 + 直接修复者（改代码 + 跑测试）"

  if ($s.convergeProposal) {
    Write-Host "⚠ 【复核确认回合】：$($s.proposer) 上一回合已声明「无剩余分歧」。"
    Write-Host "  -> 若你复核后亦无新反对，请回复「无剩余分歧」以正式收敛；"
    Write-Host "  -> 若你仍能找出新漏洞，请提出批判反驳并直接修改代码（辩论将自动继续）。"
  }

  Write-Host "`n----- 需求 request.md -----"
  Write-Host (Read-Utf8 (Join-Path $ReqDir 'request.md'))

  Write-Host "`n----- 当前未提交改动 -----"
  Get-GitSummary | ForEach-Object { Write-Host $_ }

  Write-Host "`n----- 辩论记录 debate.md 尾部 -----"
  $rec = Join-Path $ReqDir 'debate.md'
  if (Test-Path $rec) {
    Get-Content $rec -Encoding UTF8 | Select-Object -Last 40
  } else {
    Write-Host "（暂无历史记录）"
  }

  Write-Host "`n----- 你的任务 -----"
  Write-Host (Get-TaskText)

  Write-Host "`n----- 操作指引 -----"
  Write-Host "1. 请将你的审查意见、改动及验证结果完整覆盖写入草稿文件: $DraftFile"
  Write-Host "2. 确认写入完成后，在当前终端运行提交命令交接回合:"
  Write-Host "   powershell -ExecutionPolicy Bypass -File `"$ScriptPath`" -Action commit -Tool $($s.turn) -ReqName `"$ReqName`""
}

# ==================== 流程执行分支 ====================

if ($Action -eq 'init') {
  New-Item -ItemType Directory -Force -Path $ReqDir | Out-Null
  $reqFile = Join-Path $ReqDir 'request.md'
  if ($Requirement -and $Requirement.Trim()) {
    Write-Utf8 $reqFile $Requirement.Trim()
  } elseif (-not (Test-Path $reqFile)) {
    Write-Utf8 $reqFile "# 需求说明`n`n请在此写入本轮对抗辩论与修复的具体需求内容..."
  }
  $s = [pscustomobject]@{
    round            = 1
    turn             = $ToolA
    turnNo           = 0
    done             = $false
    convergeProposal = $false
    proposer         = $null
    toolA            = $ToolA
    toolB            = $ToolB
  }
  Set-State $s
  Write-Host "$Prefix 已初始化状态: $ReqDir（首轮轮到 $($s.turn)）"
  $rec = Join-Path $ReqDir 'debate.md'
  if (-not (Test-Path $rec)) {
    Write-Utf8 $rec "<!-- code-debate 初始化 -->`n## [辩论] 需求已就绪，等待 $ToolA 第一轮。`n`n"
  }
  exit 0
}

if ($Action -eq 'status') {
  $s = Ensure-Init
  $confirmInfo = if ($s.convergeProposal) { " | 待确认: $($s.proposer) 已声明收敛，等待 $($s.turn) 复核" } else { '' }
  Write-Host "$Prefix 项目根=$ProjectRoot | 批次=$ReqName"
  Write-Host "$Prefix 轮次=$($s.round) | 当前轮到=$($s.turn) | 已完成回合=$($s.turnNo) | done=$($s.done)$confirmInfo"
  $rec = Join-Path $ReqDir 'debate.md'
  Write-Host "----- debate.md 尾部 -----"
  if (Test-Path $rec) { Get-Content $rec -Encoding UTF8 | Select-Object -Last 15 } else { Write-Host "（无）" }
  exit 0
}

if ($Action -eq 'wait') {
  if (-not $Tool) { Write-Host "$Prefix 缺少参数：请指定 -Tool <工具名>"; exit 1 }
  $s = Ensure-Init
  if ($s.done) { Write-Host "$Prefix ✅ 辩论结束"; exit 0 }
  $waited = 0
  while ((Get-State).turn -ne $Tool) {
    $s = Get-State
    if ($s.done) { Write-Host "$Prefix ✅ 辩论结束"; exit 0 }
    if ($waited -ge $TimeoutSeconds) {
      Write-Host "$Prefix 未轮到你（已等待 ${waited}s）。当前轮到 $($s.turn)。请再次运行 wait 重试排队。"
      exit 0
    }
    Start-Sleep -Seconds 3
    $waited = $waited + 3
  }
  Write-Briefing (Get-State)
  exit 0
}

if ($Action -eq 'commit') {
  if (-not $Tool) { Write-Host "$Prefix 缺少参数：请指定 -Tool <工具名>"; exit 1 }
  $s = Ensure-Init
  if ($s.done) { Write-Host "$Prefix ✅ 辩论结束"; exit 0 }
  if ($s.turn -ne $Tool) { Write-Host "$Prefix 还没轮到你（当前轮到 $($s.turn)）"; exit 1 }
  if (-not (Test-Path $DraftFile)) { Write-Host "$Prefix 找不到发言草稿文件：$DraftFile，请先写入你的分析与修改说明"; exit 1 }

  $content = (Read-Utf8 $DraftFile).Trim()
  if (-not $content) { Write-Host "$Prefix 发言内容为空"; Remove-Item $DraftFile -Force; exit 1 }

  $s.turnNo = $s.turnNo + 1
  $rec = Join-Path $ReqDir 'debate.md'
  Add-Content -Path $rec -Encoding UTF8 -Value (New-TurnEntry $Tool $s.round $s.turnNo $content)
  Remove-Item $DraftFile -Force
  Write-Host "$Prefix $Tool · R$($s.round) · TURN-$($s.turnNo) 已记录追加到 debate.md"

  # ---------- 动态确定对手工具 ----------
  $tA = if ($s.toolA) { $s.toolA } else { $ToolA }
  $tB = if ($s.toolB) { $s.toolB } else { $ToolB }
  $other = if ($Tool -eq $tA) { $tB } else { $tA }

  # ---------- 收敛判定逻辑 ----------
  $declared = ((Get-ConvergenceMarker $content) -match $ConvergeRe)

  if ($declared) {
    if ($s.round -lt $MinRounds) {
      # 1. 未达最少轮数：强制继续对抗
      $s.turn = $other
      $s.round = $s.round + 1
      Set-State $s
      Write-Host "$Prefix $Tool 已声明「无剩余分歧」，但未达设定的最少审查轮数($MinRounds)，对抗继续。轮到 $other（第 $($s.round) 轮）。"
      exit 0
    }
    if ($s.convergeProposal -and $s.proposer -ne $Tool) {
      # 2. 双方均达成共识 -> 收敛结束
      Write-Utf8 (Join-Path $ReqDir 'debate-conclusion.md') (Build-Conclusion $s $content "双方在第 $($s.round) 轮达成共识，确认无剩余分歧")
      $s.done = $true
      Set-State $s
      Write-Host "$Prefix ✅ 辩论结束（第 $($s.round) 轮，双方确认无剩余分歧）。"
      Write-Host "$Prefix 最终结论已生成至: $(Join-Path $ReqDir 'debate-conclusion.md')"
      exit 0
    }
    # 3. 首方提出收敛：挂起共识提案，移交对方复核
    $s.convergeProposal = $true
    $s.proposer = $Tool
    $s.turn = $other
    $s.round = $s.round + 1
    Set-State $s
    Write-Host "$Prefix $Tool 已声明「无剩余分歧」，交由 $other 复核确认；若 $other 亦无异议则收敛，若发现新漏洞则辩论继续。轮到 $other（第 $($s.round) 轮）。"
    exit 0
  }

  # 4. 出现新异议：清空之前挂起的收敛提议
  $s.convergeProposal = $false
  $s.proposer = $null

  if ($s.round -ge $MaxRounds) {
    # 达到熔断上限
    Write-Utf8 (Join-Path $ReqDir 'debate-conclusion.md') (Build-Conclusion $s $content "达到最大轮次上限($MaxRounds)，强制结束，需人工介入裁决")
    $s.done = $true
    Set-State $s
    Write-Host "$Prefix 达到最大轮次上限($MaxRounds)，辩论强制熔断。请人工查阅 debate.md 进行裁决。"
    exit 0
  }

  # 5. 未收敛且未超限：轮换至对方
  $s.turn = $other
  $s.round = $s.round + 1
  Set-State $s
  Write-Host "$Prefix 已记录，轮换至 $other（第 $($s.round) 轮）。未轮到你时 wait 会自动排队等待。"
  exit 0
}