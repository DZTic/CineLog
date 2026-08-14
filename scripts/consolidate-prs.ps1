# ==============================================================================
# CineLog - Script de Consolidation Automatique des Pull Requests (PowerShell)
# ==============================================================================

[CmdletBinding()]
param(
    [string]$BaseBranch = "main",
    [string]$TargetBranch = "consolidate/all-prs",
    [switch]$Push,
    [switch]$CreatePr,
    [switch]$DryRun,
    [ValidateSet("asc", "desc")]
    [string]$Order = "asc"
)

$ErrorActionPreference = "Stop"

# Forcer l'encodage UTF-8 pour la communication avec git et gh CLI
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "CineLog - Consolidation des Pull Requests (PowerShell)" -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "Branche de base : $BaseBranch"
Write-Host "Branche cible   : $TargetBranch"
Write-Host "Mode Dry-Run    : $(if ($DryRun) { 'OUI' } else { 'NON' })"
Write-Host "Auto Push       : $(if ($Push) { 'OUI' } else { 'NON' })"
Write-Host "Creer/Maj PR    : $(if ($CreatePr) { 'OUI' } else { 'NON' })"
Write-Host ""

# Verifier la presence de gh CLI
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Error "L'outil GitHub CLI ('gh') n'est pas disponible dans le PATH. Veuillez l'installer ou le connecter."
    exit 1
}

# Fetch des branches distantes
Write-Host "Recuperation des donnees distantes (git fetch origin)..." -ForegroundColor Yellow
git fetch origin $BaseBranch --quiet

# Recuperer la liste des PRs ouvertes
Write-Host "Recuperation des Pull Requests ouvertes ciblant '$BaseBranch'..." -ForegroundColor Yellow
$prsRaw = gh pr list --base $BaseBranch --state open --json number,title,headRefName,url,author,isDraft --limit 100 | ConvertFrom-Json

if (-not $prsRaw -or $prsRaw.Count -eq 0) {
    Write-Host "Aucune Pull Request ouverte trouvee ciblant '$BaseBranch'." -ForegroundColor Yellow
    exit 0
}

# Filtrer si la branche cible existe deja dans les PRs
$prs = @($prsRaw | Where-Object { $_.headRefName -ne $TargetBranch })

if ($Order -eq "asc") {
    $prs = @($prs | Sort-Object -Property number)
} else {
    $prs = @($prs | Sort-Object -Property number -Descending)
}

Write-Host "$($prs.Count) Pull Request(s) a traiter." -ForegroundColor Green
Write-Host ""

# Initialiser la branche consolidee si pas en DryRun
if (-not $DryRun) {
    Write-Host "Initialisation de la branche '$TargetBranch' depuis 'origin/$BaseBranch'..." -ForegroundColor Yellow
    git checkout -B $TargetBranch "origin/$BaseBranch" --quiet
}

$mergedPrs = [System.Collections.Generic.List[object]]::new()
$conflictPrs = [System.Collections.Generic.List[object]]::new()
$skippedPrs = [System.Collections.Generic.List[object]]::new()

Write-Host "Demarrage de la fusion sequentielle :" -ForegroundColor Cyan
Write-Host "------------------------------------------------------"

foreach ($pr in $prs) {
    $num = $pr.number
    $title = $pr.title
    $branch = $pr.headRefName
    $author = $pr.author.login
    $url = $pr.url

    Write-Host "Traitement PR #$num ($branch) - $title ... " -NoNewline

    # Fetch de la branche de la PR
    $fetchProc = git fetch origin "${branch}:refs/remotes/origin/${branch}" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[BRANCHE INTROUVABLE]" -ForegroundColor Yellow
        $skippedPrs.Add([PSCustomObject]@{ Number = $num; Title = $title; Author = $author; Url = $url; Branch = $branch; Reason = "Branche introuvable" })
        continue
    }

    if ($DryRun) {
        # Simulation via merge-tree
        $mergeBase = (git merge-base HEAD "origin/$branch" 2>&1).Trim()
        $treeCheck = git merge-tree $mergeBase HEAD "origin/$branch" 2>&1
        if ($treeCheck -match "\+<<<<<<<") {
            Write-Host "[CONFLIT]" -ForegroundColor Red
            $conflictPrs.Add([PSCustomObject]@{ Number = $num; Title = $title; Author = $author; Url = $url; Branch = $branch })
        } else {
            Write-Host "[OK - SIMULE]" -ForegroundColor Green
            $mergedPrs.Add([PSCustomObject]@{ Number = $num; Title = $title; Author = $author; Url = $url; Branch = $branch })
        }
    } else {
        # Fusion reelle
        $mergeCommitMsg = "Merge PR #$num : $title (#$num)"
        $mergeOut = git merge --no-ff "origin/$branch" -m $mergeCommitMsg 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "[FUSIONNEE]" -ForegroundColor Green
            $mergedPrs.Add([PSCustomObject]@{ Number = $num; Title = $title; Author = $author; Url = $url; Branch = $branch })
        } else {
            Write-Host "[CONFLIT]" -ForegroundColor Red
            git merge --abort 2>&1 | Out-Null
            $conflictPrs.Add([PSCustomObject]@{ Number = $num; Title = $title; Author = $author; Url = $url; Branch = $branch })
        }
    }
}

Write-Host "------------------------------------------------------"
Write-Host ""
Write-Host "Resume de la consolidation :" -ForegroundColor Cyan
Write-Host "   PRs fusionnees : $($mergedPrs.Count)" -ForegroundColor Green
Write-Host "   PRs en conflit : $($conflictPrs.Count)" -ForegroundColor Red
Write-Host "   PRs ignorees   : $($skippedPrs.Count)" -ForegroundColor Yellow
Write-Host ""

# Construction du Markdown de rapport
$totalProcessed = $mergedPrs.Count + $conflictPrs.Count + $skippedPrs.Count

$reportLines = [System.Collections.Generic.List[string]]::new()
$reportLines.Add("## Consolidation des Pull Requests CineLog")
$reportLines.Add("")
$reportLines.Add("Cette branche regroupe l'integration unifiee de toutes les Pull Requests ouvertes ciblant ``$BaseBranch``.")
$reportLines.Add("")
$reportLines.Add("### Bilan de l'integration")
$reportLines.Add("- **Total PRs traitees** : $totalProcessed")
$reportLines.Add("- **Fusionnees avec succes** : $($mergedPrs.Count)")
$reportLines.Add("- **Conflits detectes** : $($conflictPrs.Count)")
$reportLines.Add("- **Ignorees / Erreurs** : $($skippedPrs.Count)")
$reportLines.Add("")
$reportLines.Add("### Pull Requests incluses ($($mergedPrs.Count))")
$reportLines.Add("| PR | Titre | Auteur | Branche |")
$reportLines.Add("|---|---|---|---|")

foreach ($item in $mergedPrs) {
    $reportLines.Add("| [#$($item.Number)]($($item.Url)) | $($item.Title) | @$($item.Author) | ``$($item.Branch)`` |")
}

if ($conflictPrs.Count -gt 0) {
    $reportLines.Add("")
    $reportLines.Add("### Pull Requests avec conflits ($($conflictPrs.Count))")
    $reportLines.Add("Les PRs suivantes n'ont pas pu etre integrees automatiquement en raison de conflits de fusion :")
    $reportLines.Add("| PR | Titre | Auteur | Branche |")
    $reportLines.Add("|---|---|---|---|")
    foreach ($item in $conflictPrs) {
        $reportLines.Add("| [#$($item.Number)]($($item.Url)) | $($item.Title) | @$($item.Author) | ``$($item.Branch)`` |")
    }
    $reportLines.Add("")
    $reportLines.Add("> [!WARNING]")
    $reportLines.Add("> Pour integrer ces PRs, rebasez-les sur la branche consolidee ``$TargetBranch`` ou resolvez les conflits avec l'agent ``pr-conflict-guardian``.")
}

$summary = [string]::Join("`n", $reportLines)

# Sauvegarde locale du rapport
$reportDir = "build/reports"
if (-not (Test-Path $reportDir)) {
    New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
}
$reportPath = "$reportDir/pr-consolidation-summary.md"
[System.IO.File]::WriteAllText($reportPath, $summary, [System.Text.Encoding]::UTF8)
Write-Host "Rapport genere : $reportPath" -ForegroundColor Cyan

# Push si demande
if ($Push -and -not $DryRun -and $mergedPrs.Count -gt 0) {
    Write-Host ""
    Write-Host "Publication de la branche '$TargetBranch' sur origin..." -ForegroundColor Yellow
    git push -u origin $TargetBranch --force
    Write-Host "Branche '$TargetBranch' poussee avec succes." -ForegroundColor Green
}

# Creation ou mise a jour de la PR
if ($CreatePr -and -not $DryRun -and $mergedPrs.Count -gt 0) {
    Write-Host ""
    Write-Host "Creation ou mise a jour de la Pull Request de consolidation..." -ForegroundColor Yellow
    $prTitle = "[Consolidation] Fusion combinee de $($mergedPrs.Count) PRs vers $BaseBranch"
    
    $existingPr = gh pr list --head $TargetBranch --base $BaseBranch --json number --jq '.[0].number' 2>$null
    if ($existingPr) {
        Write-Host "Mise a jour de la PR existante #$existingPr..."
        gh pr edit $existingPr --title $prTitle --body $summary 2>$null
        Write-Host "PR #$existingPr mise a jour." -ForegroundColor Green
    } else {
        Write-Host "Creation d'une nouvelle Pull Request..."
        $newPrUrl = gh pr create --base $BaseBranch --head $TargetBranch --title $prTitle --body $summary --label "consolidated-pr" 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Pull Request creee : $newPrUrl" -ForegroundColor Green
        } else {
            Write-Host "Note : La branche a ete poussee. Pour ouvrir la PR manuellement : gh pr create --base $BaseBranch --head $TargetBranch" -ForegroundColor Yellow
        }
    }
}

Write-Host ""
Write-Host "Consolidation terminee avec succes !" -ForegroundColor Green
