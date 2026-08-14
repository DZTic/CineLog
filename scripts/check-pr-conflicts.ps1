# CineLog - PR Conflict Checker (PowerShell)
param(
    [string]$TargetBranch = "origin/main"
)

$currentBranch = git rev-parse --abbrev-ref HEAD 2>$null
if (-not $currentBranch) { $currentBranch = "HEAD" }

Write-Host "Verifiant les conflits entre $currentBranch et $TargetBranch..." -ForegroundColor Cyan

$targetRef = $TargetBranch
$refCheck = git rev-parse --verify $targetRef 2>$null
if (-not $refCheck) {
    $refCheck = git rev-parse --verify "origin/$TargetBranch" 2>$null
    if ($refCheck) {
        $targetRef = "origin/$TargetBranch"
    } else {
        git fetch origin $TargetBranch 2>$null
    }
}

$mergeOutput = (git merge-tree --write-tree HEAD $targetRef 2>&1 | Out-String)
$lastExitCode = $LASTEXITCODE

$hasConflict = ($lastExitCode -ne 0) -or ($mergeOutput -like "*CONFLICT*") -or ($mergeOutput -like "*changed in both*")

if ($hasConflict) {
    Write-Host "========================================================================" -ForegroundColor Red
    Write-Host "CONFLIT DE FUSION DETECTE !" -ForegroundColor Red
    Write-Host "========================================================================" -ForegroundColor Red
    Write-Host "La branche $currentBranch ne peut pas etre fusionnee proprement dans $targetRef." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Fichiers en conflit :" -ForegroundColor Yellow
    $lines = $mergeOutput -split "`r?`n"
    foreach ($line in $lines) {
        if ($line -like "*CONFLICT*" -or $line -like "*Auto-merging*") {
            Write-Host "   $line" -ForegroundColor Red
        }
    }
    Write-Host ""
    Write-Host "Procedure recommandee :" -ForegroundColor Cyan
    Write-Host "   1. git fetch origin main"
    Write-Host "   2. git rebase origin/main"
    Write-Host "   3. Resoudre les conflits puis git rebase --continue"
    Write-Host "   4. git push --force-with-lease origin $currentBranch"
    Write-Host "========================================================================" -ForegroundColor Red
    exit 1
} else {
    Write-Host "Aucun conflit detecte avec $targetRef. Fusion propre garantie." -ForegroundColor Green
    exit 0
}
