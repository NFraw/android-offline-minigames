. "$PSScriptRoot/Common.ps1"
$source = [IO.File]::ReadAllText((Join-Path $ProjectRoot 'app/src/main/java/com/minigames/collection/common/GameSpec.kt'))
$games = [regex]::Matches($source, '(?s)GameSpec\("([^"]+)",\s*"([^"]+)",\s*GameCategory\.(\w+).*?(?=\s*GameSpec\(|\s*\n    \))')
if ($games.Count -ne 37) { throw "注册表解析数量异常：$($games.Count)" }
$lines = @('extends RefCounted','const GAMES = {')
$report = @()
foreach ($game in $games) {
    $id = $game.Groups[1].Value
    $scene = [regex]::Match($game.Value, 'GameRuntime.Godot\("([^"]+)"').Groups[1].Value
    $landscape = $game.Value.Contains('landscape = true')
    if ($scene -and -not (Test-Path (Join-Path $GodotProject ($scene -replace '^res://','')))) { throw "场景不存在：$scene" }
    $quoted = [regex]::Matches($game.Value, '"((?:\\.|[^"\\])*)"')
    $description = if ($quoted.Count -gt 4) { $quoted[3].Groups[1].Value } else { '' }
    $rules = if ($quoted.Count -gt 4) { $quoted[4].Groups[1].Value } else { '' }
    $metadata = [ordered]@{title=$game.Groups[2].Value; category=$game.Groups[3].Value; scene=$scene; landscape=$landscape; description=$description; rules=$rules; educational=$game.Value.Contains('educational = true')}
    $lines += '    "'+$id+'": '+($metadata | ConvertTo-Json -Compress -Depth 3)+','
    $report += [pscustomobject]@{id=$id; title=$game.Groups[2].Value; runtime=$(if($scene){'Godot'}else{'Canvas'}); scene=$scene}
}
$lines += '}'
[IO.File]::WriteAllText((Join-Path $GodotProject 'games/suite/catalog.gd'), ($lines -join [char]10), [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $ProjectRoot 'docs/development/迁移路由清单.json'), ($report | ConvertTo-Json -Depth 4), [Text.UTF8Encoding]::new($false))
Write-Output "Godot $(@($report | Where-Object runtime -eq 'Godot').Count) / $($games.Count)"
