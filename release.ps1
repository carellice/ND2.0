# ------------------------------------------------------------------
#  ND2.0 – pubblica una nuova versione su GitHub (Windows)
#
#  Doppio clic su release.cmd per:
#   1. incrementare la versione (versionCode +1, versionName 1.N -> 1.N+1)
#   2. compilare l'APK release
#   3. fare commit e push del cambio versione
#   4. creare la release su GitHub con allegato ND2.0.apk
#   5. eliminare le release (e i tag) precedenti
# ------------------------------------------------------------------
$ErrorActionPreference = 'Stop'

Set-Location $PSScriptRoot
$Repo = 'carellice/ND2.0'
$GradleFile = 'app/build.gradle.kts'

function Bold($text) { Write-Host "`n$text" -ForegroundColor Magenta }
function Fail($text) {
    Write-Host "`n✖ $text" -ForegroundColor Red
    Read-Host 'Premi Invio per chiudere…' | Out-Null
    exit 1
}
# Esegue un comando esterno e si ferma se fallisce.
function Run {
    & $args[0] @($args | Select-Object -Skip 1)
    if ($LASTEXITCODE -ne 0) { throw "Comando fallito: $($args -join ' ')" }
}

try {
    # JDK: usa quello di Android Studio se non ne è impostato uno.
    if (-not $env:JAVA_HOME -and (Test-Path "$env:ProgramFiles\Android\Android Studio\jbr")) {
        $env:JAVA_HOME = "$env:ProgramFiles\Android\Android Studio\jbr"
    }

    # git: se non è nel PATH usa quello di GitHub Desktop.
    $Git = (Get-Command git -ErrorAction SilentlyContinue).Source
    if (-not $Git) {
        $Git = Get-ChildItem "$env:LOCALAPPDATA\GitHubDesktop\app-*\resources\app\git\cmd\git.exe" -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    }
    if (-not $Git) { Fail 'git non trovato. Installalo con: winget install Git.Git' }

    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { Fail 'GitHub CLI (gh) non trovato. Installalo con: winget install GitHub.cli' }
    gh auth status *> $null
    if ($LASTEXITCODE -ne 0) { Fail 'Non sei autenticato su GitHub. Esegui: gh auth login' }

    # ---- 1. Versione -------------------------------------------------
    $gradle = Get-Content $GradleFile -Raw
    $OldCode = [int]([regex]::Match($gradle, '(?m)^\s*versionCode = (\d+)').Groups[1].Value)
    $OldName = [regex]::Match($gradle, '(?m)^\s*versionName = "([^"]+)"').Groups[1].Value
    $parts = $OldName.Split('.')
    $NewCode = $OldCode + 1
    $NewName = "$($parts[0]).$([int]$parts[1] + 1)"
    $Tag = "v$NewName"

    Bold '🌙 ND2.0 – nuova release'
    Write-Host "Versione attuale: $OldName ($OldCode)"
    Write-Host "Nuova versione:   $NewName ($NewCode)"
    Write-Host
    $Notes = Read-Host 'Note di rilascio (Invio per saltare)'
    if ([string]::IsNullOrWhiteSpace($Notes)) { $Notes = 'Nuova versione di ND2.0.' }

    $gradle = $gradle -replace '(versionCode = )\d+', "`${1}$NewCode" -replace '(versionName = ")[^"]+', "`${1}$NewName"
    [IO.File]::WriteAllText((Resolve-Path $GradleFile), $gradle)

    # ---- 2. Build ----------------------------------------------------
    Bold "🔨 Compilo l'APK…"
    Run .\gradlew.bat -q assembleRelease
    New-Item -ItemType Directory -Force build | Out-Null
    Copy-Item app/build/outputs/apk/release/app-release.apk build/ND2.0.apk -Force

    # ---- 3. Commit e push --------------------------------------------
    Bold '📤 Commit e push…'
    Run $Git add -A
    Run $Git commit -q -m "Release $Tag"
    Run $Git push -q origin HEAD

    # ---- 4. Release --------------------------------------------------
    Bold "🚀 Creo la release $Tag…"
    Run gh release create $Tag build/ND2.0.apk --repo $Repo --title "ND2.0 $NewName" --notes $Notes --latest

    # ---- 5. Pulizia release precedenti --------------------------------
    Bold '🧹 Elimino le release precedenti…'
    $tags = gh release list --repo $Repo --limit 100 --json tagName -q '.[].tagName'
    foreach ($OldTag in $tags) {
        if ($OldTag -and $OldTag -ne $Tag) {
            Write-Host "  - $OldTag"
            Run gh release delete $OldTag --repo $Repo --yes --cleanup-tag
        }
    }

    Bold "✅ Fatto! ND2.0 $NewName è online:"
    Write-Host "https://github.com/$Repo/releases/tag/$Tag"
    Write-Host
    Read-Host 'Premi Invio per chiudere…' | Out-Null
}
catch {
    Fail "Qualcosa è andato storto: $($_.Exception.Message)"
}
