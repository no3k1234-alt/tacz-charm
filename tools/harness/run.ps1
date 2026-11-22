# Offline charm physics harness. Keep this file ASCII: Windows PowerShell 5.1 reads BOM-less UTF-8 as ANSI.
# Build the mod first (gradlew build). charm_dump.txt comes from the in-game check (/charm command or -Dtaczcharm.selfTest=true).
# Usage: .\tools\harness\run.ps1 -Dump <charm_dump.txt> [-Only id,id|-] [-Gun <id> -Scenario <n> -Start <n>]
# -Gun/-Scenario/-Start traces a single run. Otherwise the report goes to charm_harness.txt next to the dump,
# in the same format as the in-game charm_selftest.txt.
param([string]$Dump, [string]$Only = '-', [string]$Gun = '', [string]$Scenario = '', [string]$Start = '')
$root = Split-Path (Split-Path $PSScriptRoot)
$jdk = (Get-ChildItem "$env:USERPROFILE\.gradle\jdks" -Directory -Filter 'eclipse_adoptium-21*' | Select-Object -First 1).FullName
$joml = (Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.joml\joml\1.10.9" -Recurse -Filter 'joml-1.10.9.jar' | Select-Object -First 1).FullName
$cp = "$root\build\libs\TaczCharm-0.2.0.jar;$joml;$root\libs\tacz-refabricated-1.0.1-neo1.2.0-hotfix+mc1.21.1.jar"
$out = Join-Path $env:TEMP 'taczcharm-harness'
New-Item -ItemType Directory -Force $out | Out-Null
& "$jdk\bin\javac.exe" -nowarn -encoding UTF-8 -cp $cp -d $out "$PSScriptRoot\CharmHarness.java"
$a = @($Dump, $Only)
if ($Gun) { $a += @($Gun, $Scenario, $Start) }
# mod classes reference Minecraft classes the harness never calls -> skip bytecode verification
& "$jdk\bin\java.exe" -XX:+UnlockDiagnosticVMOptions -XX:-BytecodeVerificationRemote -XX:-BytecodeVerificationLocal "-Dstdout.encoding=UTF-8" -cp "$out;$cp" com.markwarfare.charm.client.CharmHarness @a
