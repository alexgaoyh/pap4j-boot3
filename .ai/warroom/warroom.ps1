# ============================================================
# warroom.ps1 — 跨工具回合协调脚本 v1
#
# 核心机制: 两个 AI 工具各自调用本脚本，脚本负责"排队、追加、判断收敛、推进阶段"。
#           同一时刻只有一个工具在写；未轮到你时 wait 有界等待（-TimeoutSeconds，默认 90s）。
#           收敛靠内容判定: 发言若含"无剩余分歧/无新反对/已统一/无歧义"即冻结并进入下一站。
#
# 使用方式（在已登录 agy 的 PowerShell 中）:
#   ① 需求进系统一次（二选一）:
#      - 手写 .ai/warroom/req-active/request.md（白话即可，脚本第一次被调用时自动初始化）
#      - 或: powershell -ExecutionPolicy Bypass -File .ai/warroom/warroom.ps1 -Action init -ReqName active -ReqText "需求"
#   ② 开两个 PowerShell 窗口（都停在项目根目录），分别启动 claude / agy，各粘贴下面对应的启动指令。
#
#   【窗口 1 · claude】: 启动 `claude` 后粘贴:
#     你是 warroom 辩论中的 claude。需求已写在 D:\ideaprojects\pap4j-boot3\.ai\warroom\req-active\request.md。
#     循环执行以下步骤，直到脚本输出"全部完成"：
#     1. 运行：powershell -ExecutionPolicy Bypass -File D:\ideaprojects\pap4j-boot3\.ai\warroom\warroom.ps1 -Action wait -Tool claude
#     2. 若输出含"未轮到你"：回到步骤 1（wait 有界等待，没轮到你时返回提示，重试即可）。
#     3. 若输出含"轮到你"：按简报把你的发言正文（Markdown）写入 D:\ideaprojects\pap4j-boot3\.ai\warroom\req-active\claude.draft.md（覆盖，不要改其它任何文件）。
#     4. 运行：powershell -ExecutionPolicy Bypass -File D:\ideaprojects\pap4j-boot3\.ai\warroom\warroom.ps1 -Action commit -Tool claude
#     5. 回到步骤 1。
#
#   【窗口 2 · agy】: 启动 `agy` 后粘贴:
#     你是 warroom 辩论中的 agy。需求已写在 D:\ideaprojects\pap4j-boot3\.ai\warroom\req-active\request.md。
#     循环执行以下步骤，直到脚本输出"全部完成"：
#     1. 运行：powershell -ExecutionPolicy Bypass -File D:\ideaprojects\pap4j-boot3\.ai\warroom\warroom.ps1 -Action wait -Tool agy
#     2. 若输出含"未轮到你"：回到步骤 1（wait 有界等待，没轮到你时返回提示，重试即可）。
#     3. 若输出含"轮到你"：按简报把你的发言正文（Markdown）写入 D:\ideaprojects\pap4j-boot3\.ai\warroom\req-active\agy.draft.md（覆盖，不要改其它任何文件）。
#     4. 运行：powershell -ExecutionPolicy Bypass -File D:\ideaprojects\pap4j-boot3\.ai\warroom\warroom.ps1 -Action commit -Tool agy
#     5. 回到步骤 1。
#
#   其它:
#     status          查看当前状态
#     -ReqName        需求目录名（默认 active）
#     -MaxRounds      轮次上限（默认 5，到顶未收敛则强制冻结并标注需人工确认）
#     -TimeoutSeconds wait 单次等待上限（默认 90，返回"未轮到你"后重试即可）
# ============================================================
param(
  [ValidateSet('init','status','wait','commit')][string]$Action = 'status',
  [ValidateSet('claude','agy')][string]$Tool,
  [Alias('r')][string]$ReqText,
  [string]$ReqName = 'active',
  [int]$MaxRounds = 5,
  [int]$TimeoutSeconds = 90
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Prefix = '[AI-Agent-Runner]'
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$ScriptRoot = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
$ReqDir = Join-Path $ScriptRoot "req-$ReqName"
$StateFile = Join-Path $ReqDir 'state.json'
$DraftFile = Join-Path $ReqDir "$Tool.draft.md"

# ---------- 站配置 ----------
$Stages        = @('s0','s1','s2','s3','s4','s5','s6')
$StageDirs     = @('s0-decompose','s1-profile','s2-design','s3-pre-coding','s4-execution','s5-quality-gate','s6-finalize')
$StageNames    = @('需求接入与拆解','任务画像与自检清单','前置门控与方案质询','编码前查重与基桩','执行循环','分级质量门禁','收尾提交')
$StageMain     = @('claude','agy','claude','agy','claude','agy','claude')
$StageCritic   = @('agy','claude','agy','claude','agy','none','agy')
$StageMainRole = @('拆解者','画像者','设计者','查重者','实施方案者','独立审查者','收尾起草者')
$StageCriticRole = @('批判者','清单批判者','质询者','基桩批判者','方案批判者','none','收尾批判者')
$ConvergeRe    = '无剩余分歧|无新反对|已统一|无歧义'

# ---------- 工具函数 ----------
function Write-Utf8 { param($Path,$Content) [IO.File]::WriteAllText($Path,$Content,$Utf8NoBom) }
function Read-Utf8 { param($Path) if (Test-Path $Path) { [IO.File]::ReadAllText($Path) } else { '' } }
function Get-StageIndex { param([string]$s) for ($i=0;$i -lt $Stages.Count;$i++){ if ($Stages[$i] -eq $s){ return $i } } return -1 }

function Get-State {
  if (-not (Test-Path $StateFile)) { return $null }
  (Read-Utf8 $StateFile) | ConvertFrom-Json
}
function Set-State { param($s) Write-Utf8 $StateFile ($s | ConvertTo-Json -Compress) }

# 自动初始化：state 缺失但 request.md 存在时，自动建状态并播种 s0，无需单独跑 init
function Ensure-Init {
  $s = Get-State
  if ($s) { return $s }
  if (-not (Test-Path (Join-Path $ReqDir 'request.md'))) {
    Write-Host "$Prefix 缺少需求：请先写 $ReqDir\request.md，或运行 -Action init -ReqText '需求'"
    exit 1
  }
  New-Item -ItemType Directory -Force -Path $ReqDir | Out-Null
  $s = [pscustomobject]@{ stage='s0'; round=1; turn=$StageMain[0]; turnNo=0; done=$false }
  Set-State $s
  Seed-Stage 's0'
  Write-Host "$Prefix 已自动初始化（需求来自 request.md），轮到 $($s.turn)"
  return $s
}

# ---------- 角色任务块 ----------
function Get-TaskDraft {
  param([string]$s)
  switch ($s) {
    's0' { return @'
按 task-executor 阶段0（task-breakdown）拆解需求：
- 输出 mini-PRD + 原子化 Task 列表，每个 Task 可独立验收、无职责重叠、有明确顺序依赖
- 显式列出你默认的关键假设
'@ }
    's1' { return @'
按 task-executor 阶段1 任务画像：
- 输出一行 [Profile: Scope|Type|Area|Impact] 标签
- 依据标签输出专属自检清单（对照清单池）
- 列出打标依赖的关键假设
'@ }
    's2' { return @'
基于上游结论输出设计方案（等价 task-executor 阶段2 [Plan]）：
- 变更范围（文件列表+子模块）/ 技术选型理由 / 风险与回滚 / 按序执行步骤 / 验证命令
- 说明如何满足 guard.md 红线；列出关键假设
'@ }
    's3' { return @'
按 task-executor 阶段3 编码前准备：
- 涉及 common 工具 → 检索 .ai/utilities.md 防重；涉及复杂接口 → 生成 MockMvc 离线基桩
- 输出防重结论与基桩覆盖范围；列出关键假设
'@ }
    's4' { return @'
基于上游设计，输出执行方案：
- 改动文件/方法、是否走 tdd（红-绿-重构）、验证命令（按环境选 Git Bash / PowerShell）
- 说明不违反 guard.md；列出关键假设
'@ }
    's5' { return @'
对 git diff 与设计结论做独立批判性审查：
- 红线合规（guard.md）/ NPE 与极值漏洞 / 单测覆盖 / 调试残留
- 对照设计结论逐条核对实现一致性
- 结论：放行 / 打回（打回须附具体修复点）
'@ }
    's6' { return @'
按 task-executor 阶段6 收尾：
- 依据 s5 审查结论与实现，起草 Conventional Commit Message（feat:/fix:/refactor: 等）+ 简短 Changelog
- 输出格式：第一行标题，空行后正文，最后 Changelog 节
'@ }
  }
}

function Get-TaskCritic {
  param([string]$s)
  $base = switch ($s) {
    's0' { @'
对辩论记录中最新的主笔草稿做对抗审查：
- 找出 3-5 个具体漏洞（顺序依赖/边界/职责重叠/范围蔓延/未言明假设）；试图证伪
- 禁止写"整体看起来没问题"；逐条给出修改建议
'@ }
    's1' { @'
对画像标签与自检清单做对抗审查：
- 找遗漏维度/遗漏清单项（如遗漏 persistence/concurrency/breaking-api 标签）；检查清单过载或不足
- 禁止写"标签合理"；逐条给建议
'@ }
    's2' { @'
对设计方案发起苏格拉底式反向质询：
- 3-5 项质询，聚焦破坏性契约/并发/持久层/边界情况；每项附"若不解决会发生什么"
- 禁止写"方案可行"
'@ }
    's3' { @'
对防重结论与基桩清单做对抗审查：
- 检查是否漏查既有工具（重复造轮子）、基桩是否覆盖嵌套 DTO/鉴权/三方调用
- 禁止写"无重复"；逐条给建议
'@ }
    's4' { @'
对执行方案做对抗审查：
- 找实现层面的洞：破坏既有行为/边界处理/测试覆盖缺失/命令可执行性
- 禁止写"方案可行"
'@ }
    's6' { @'
对 Commit/Changelog 草案做对抗审查：
- 核对 message 与真实 git diff 一致、scope 正确、无缺失/夸大
- 禁止写"没问题"；逐条给修正
'@ }
  }
  return $base + @'
若你已无新的反对意见，请在你回复的**结尾独立一行**写：
无剩余分歧
'@
}

function Get-TaskRevise {
  return @'
根据辩论记录中批判者的意见，修订你的上一版：
- 逐条回应：接受 / 拒绝 + 理由
- 输出修订后的完整最终稿（不要只给差异）
若批判意见已全部处理、无遗留歧义，请在你回复的**结尾独立一行**写：
已统一
'@
}

# ---------- 文件协议 ----------
function New-TurnEntry {
  param($tool,$role,$turnNo,$body)
  $stamp = Get-Date -Format 'yyyy-MM-dd HH:mm'
  $sb = New-Object System.Text.StringBuilder
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("<!-- TURN-$turnNo -->")
  [void]$sb.AppendLine("## [$tool · $role · R$turnNo] $stamp")
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine($body)
  [void]$sb.AppendLine("")
  return $sb.ToString()
}

function Get-LastMainBody {
  param($sd,$mainTool)
  $of = Join-Path $ReqDir "$sd\outputs.md"
  if (-not (Test-Path $of)) { return '' }
  $lines = Get-Content $of -Encoding UTF8
  $re = "^## \[$([regex]::Escape($mainTool))"
  $lastIdx = -1
  for ($k=0; $k -lt $lines.Count; $k++) { if ($lines[$k] -match $re) { $lastIdx = $k } }
  if ($lastIdx -lt 0) { return '' }
  $body = @()
  for ($k=$lastIdx+1; $k -lt $lines.Count; $k++) {
    if ($lines[$k] -match '^<!-- TURN-') { break }
    $body += $lines[$k]
  }
  return ($body -join "`n")
}

function Freeze-Station {
  param($stage,$body,$round,$note)
  $i = Get-StageIndex $stage
  $sd = $StageDirs[$i]; $sname = $StageNames[$i]
  $next = if ($i+1 -lt $Stages.Count) { $Stages[$i+1] } else { '✅ 全部完成' }
  $prev = if ($i -gt 0) { $StageNames[$i-1] } else { '无' }
  $cf = Join-Path $ReqDir "$sd\conclusion.md"
  $sb = New-Object System.Text.StringBuilder
  [void]$sb.AppendLine("> 阶段: $stage $sname")
  [void]$sb.AppendLine("> 下一棒: $next")
  [void]$sb.AppendLine("> 状态: ✅ 已统一 (round $round)$(if ($note) { ' ｜ ' + $note } else { '' })")
  [void]$sb.AppendLine("> 输入: $prev")
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("# 统一结论")
  [void]$sb.AppendLine($body)
  if ($body -notmatch '关键假设') {
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("## 关键假设 Key Assumptions")
    [void]$sb.AppendLine("（最终稿未显式列出，需人工补充）")
  }
  Write-Utf8 $cf $sb.ToString()
}

function Seed-Stage {
  param($stage)
  $i = Get-StageIndex $stage
  $sd = $StageDirs[$i]; $sname = $StageNames[$i]
  $dir = Join-Path $ReqDir $sd
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  $cf = Join-Path $dir 'conclusion.md'
  $of = Join-Path $dir 'outputs.md'
  if (-not (Test-Path $cf)) {
    Write-Utf8 $cf "> 阶段: $stage $sname`n> 下一棒: <待定>`n> 状态: 🔄 辩论中`n> 输入: <待定>`n`n# 统一结论`n（待统一后写入）`n"
  }
  if (-not (Test-Path $of)) {
    $prev = if ($i -gt 0) { $StageNames[$i-1] } else { '需求单' }
    Write-Utf8 $of "<!-- TURN-0 -->`n## [站 · 输入] 输入 = $prev`n`n"
  }
}

function Write-Briefing {
  param($s)
  $i = Get-StageIndex $s.stage
  $sd = $StageDirs[$i]; $sname = $StageNames[$i]
  $isMain = ($s.turn -eq $StageMain[$i])
  $role = if ($isMain) { $StageMainRole[$i] } else { $StageCriticRole[$i] }
  $task = if ($isMain) { if ($s.round -le 1) { Get-TaskDraft $s.stage } else { Get-TaskRevise } } else { Get-TaskCritic $s.stage }

  Write-Host ""
  Write-Host "===== warroom 回合协调 ====="
  Write-Host "阶段: $($s.stage) $sname | 第 $($s.round) 轮 | 轮到你: $($s.turn)"
  Write-Host "角色: $role"
  Write-Host "----- 需求 -----"
  Write-Host (Read-Utf8 (Join-Path $ReqDir 'request.md'))
  if ($i -gt 0) {
    $up = Join-Path $ReqDir "$($StageDirs[$i-1])\conclusion.md"
    if (Test-Path $up) {
      Write-Host "----- 上游结论 -----"
      Write-Host (Read-Utf8 $up)
    }
  }
  Write-Host "----- 当前辩论记录 -----"
  Write-Host (Read-Utf8 (Join-Path $ReqDir "$sd\outputs.md"))
  Write-Host "----- 你的任务 -----"
  Write-Host $task
  Write-Host "----- 规则 -----"
  Write-Host "1. 只把你的发言正文写入: $DraftFile（覆盖写入，Markdown）"
  Write-Host "2. 不要修改除 $DraftFile 之外的任何文件"
  Write-Host "3. 写完后运行:"
  Write-Host "   powershell -ExecutionPolicy Bypass -File $(Join-Path $ScriptRoot 'warroom.ps1') -Action commit -Tool $($s.turn)"
}

# ---------- 动作 ----------
if ($Action -eq 'init') {
  New-Item -ItemType Directory -Force -Path $ReqDir | Out-Null
  if ($ReqText) { Write-Utf8 (Join-Path $ReqDir 'request.md') $ReqText }
  if (-not (Test-Path (Join-Path $ReqDir 'request.md'))) {
    Write-Host "$Prefix 缺少需求：请用 -ReqText '需求' 或先写 $ReqDir\request.md"; exit 1
  }
  $s = [pscustomobject]@{ stage='s0'; round=1; turn=$StageMain[0]; turnNo=0; done=$false }
  Set-State $s
  Seed-Stage 's0'
  Write-Host "$Prefix 已初始化: $ReqDir"; Write-Host "$Prefix 轮到: $($s.turn)（两个工具分别运行 wait）"
  exit 0
}

if ($Action -eq 'status') {
  $s = Ensure-Init
  $i = Get-StageIndex $s.stage
  Write-Host "$Prefix 阶段=$($s.stage) $($StageNames[$i]) | 轮次=$($s.round) | 当前轮到=$($s.turn) | done=$($s.done)"
  $of = Join-Path $ReqDir "$($StageDirs[$i])\outputs.md"
  Write-Host "----- outputs.md 尾部 -----"
  if (Test-Path $of) { Get-Content $of -Encoding UTF8 | Select-Object -Last 15 } else { Write-Host "（无）" }
  exit 0
}

if ($Action -eq 'wait') {
  if (-not $Tool) { Write-Host "$Prefix 需要 -Tool claude|agy"; exit 1 }
  $s = Ensure-Init
  if ($s.done) { Write-Host "$Prefix 全部阶段已完成"; exit 0 }
  $waited = 0
  while ((Get-State).turn -ne $Tool) {
    $s = Get-State
    if ($s.done) { Write-Host "$Prefix 全部阶段已完成"; exit 0 }
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
  if ($s.done) { Write-Host "$Prefix 全部阶段已完成"; exit 0 }
  if ($s.turn -ne $Tool) { Write-Host "$Prefix 还没轮到你（当前轮到 $($s.turn)）"; exit 1 }
  if (-not (Test-Path $DraftFile)) { Write-Host "$Prefix 找不到 $DraftFile，请先写你的发言"; exit 1 }

  $i = Get-StageIndex $s.stage
  $sd = $StageDirs[$i]; $smain = $StageMain[$i]; $scritic = $StageCritic[$i]
  $role = if ($Tool -eq $smain) { $StageMainRole[$i] } else { $StageCriticRole[$i] }
  $content = (Read-Utf8 $DraftFile).Trim()
  if (-not $content) { Write-Host "$Prefix 发言为空"; Remove-Item $DraftFile -Force; exit 1 }

  $s.turnNo = $s.turnNo + 1
  $of = Join-Path $ReqDir "$sd\outputs.md"
  Add-Content -Path $of -Encoding UTF8 -Value (New-TurnEntry $Tool $role $s.turnNo $content)
  Remove-Item $DraftFile -Force
  Write-Host "$Prefix $Tool · $role · TURN-$($s.turnNo) 已追加"

  # 收敛判定：内容含收敛信号；或该站批判者为 none（单角色直接过）
  $converged = ($content -match $ConvergeRe) -or ($scritic -eq 'none')

  if ($converged) {
    $lastBody = Get-LastMainBody $sd $smain
    $note = ''
    if ($content -notmatch $ConvergeRe) { $note = '单角色站' }
    Freeze-Station $s.stage $lastBody $s.round $note
    Write-Host "$Prefix 已收敛，冻结: $sd\conclusion.md"
    if ($i+1 -lt $Stages.Count) {
      $s.stage = $Stages[$i+1]; $s.round = 1; $s.turnNo = 0; $s.turn = $StageMain[$i+1]
      Set-State $s
      Seed-Stage $s.stage
      Write-Host "$Prefix 进入下一站 $($s.stage) $($StageNames[$i+1])，轮到 $($s.turn)"
    } else {
      $s.done = $true
      Set-State $s
      Write-Host "$Prefix 全部阶段完成。"
    }
    exit 0
  }

  if ($s.round -ge $MaxRounds) {
    $lastBody = Get-LastMainBody $sd $smain
    Freeze-Station $s.stage $lastBody $s.round "达到轮次上限($MaxRounds)，需人工确认"
    Write-Host "$Prefix 达到轮次上限，强制冻结，需人工确认。"
    if ($i+1 -lt $Stages.Count) {
      $s.stage = $Stages[$i+1]; $s.round = 1; $s.turnNo = 0; $s.turn = $StageMain[$i+1]
      Set-State $s; Seed-Stage $s.stage
      Write-Host "$Prefix 进入下一站 $($s.stage)，轮到 $($s.turn)"
    } else { $s.done = $true; Set-State $s; Write-Host "$Prefix 全部阶段完成。" }
    exit 0
  }

  # 未收敛：移交
  if ($Tool -eq $smain) {
    $s.turn = $scritic
  } else {
    $s.turn = $smain; $s.round = $s.round + 1
  }
  Set-State $s
  Write-Host "$Prefix 已记录，轮到 $($s.turn)（第 $($s.round) 轮）。未轮到你时 wait 会阻塞等待。"
  exit 0
}
