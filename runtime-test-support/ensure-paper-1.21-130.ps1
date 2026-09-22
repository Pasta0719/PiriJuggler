param()
$ErrorActionPreference = "Stop"
$dest = Join-Path $PSScriptRoot "..\runtime-evidence\PHASE_01\work\downloads\paper-1.21-130.jar"
$expected = "ab9bb1afc3cea6978a0c03ce8448aa654fe8a9c4dddf341e7cbda1b0edaa73f5"
if (Test-Path $dest) {
  $actual=(Get-FileHash $dest -Algorithm SHA256).Hash.ToLower()
  if ($actual -eq $expected) { Write-Host "Verified Paper 1.21 build 130 already present."; exit 0 }
  Remove-Item $dest -Force
}
New-Item -ItemType Directory -Force (Split-Path $dest) | Out-Null
$headers=@{"User-Agent"="PiriJuggler-runtime-tests/1.0 (https://github.com/Pasta0719/PiriJuggler)"}
$builds=Invoke-RestMethod -Headers $headers -Uri "https://fill.papermc.io/v3/projects/paper/versions/1.21/builds"
$build=$builds | Where-Object { $_.id -eq 130 } | Select-Object -First 1
if ($null -eq $build) { throw "Paper 1.21 build 130 missing" }
$url=$build.downloads.'server:default'.url
if ([string]::IsNullOrWhiteSpace($url)) { throw "Paper build 130 download URL missing" }
Invoke-WebRequest -Headers $headers -Uri $url -OutFile $dest
$actual=(Get-FileHash $dest -Algorithm SHA256).Hash.ToLower()
if ($actual -ne $expected) { throw "Paper SHA256 mismatch: $actual" }
Write-Host "Verified Paper 1.21 build 130 downloaded."
