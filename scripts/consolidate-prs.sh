#!/usr/bin/env bash
# ==============================================================================
# CinéLog - Script de Consolidation Automatique des Pull Requests (Bash)
# ==============================================================================
# Ce script récupère toutes les Pull Requests ouvertes ciblant une branche de base
# (ex: main) et tente de les fusionner séquentiellement dans une branche consolidée.
#
# Prérequis : git, gh (GitHub CLI) connecté et jq (ou fallback interne)
# ==============================================================================

set -eo pipefail

# Couleurs pour la sortie terminal
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

BASE_BRANCH="main"
TARGET_BRANCH="consolidate/all-prs"
DO_PUSH=false
DO_CREATE_PR=false
DRY_RUN=false
ORDER="asc"

# Afficher l'aide
show_help() {
    cat << EOF
Usage: $(basename "$0") [options]

Options:
  -b, --base <branch>         Branche de base cible (défaut: main)
  -t, --target <branch>       Branche consolidée à créer (défaut: consolidate/all-prs)
  -p, --push                  Pousser la branche consolidée sur origin
      --create-pr             Créer ou mettre à jour la PR consolidée sur GitHub
  -d, --dry-run               Mode simulation sans modifier les branches distantes
  -o, --order <asc|desc>      Ordre de traitement des PRs (défaut: asc)
  -h, --help                  Afficher cette aide

Exemples :
  ./scripts/consolidate-prs.sh --dry-run
  ./scripts/consolidate-prs.sh --base main --target consolidate/all-prs --push --create-pr
EOF
}

# Parser les arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        -b|--base)
            BASE_BRANCH="$2"
            shift 2
            ;;
        -t|--target)
            TARGET_BRANCH="$2"
            shift 2
            ;;
        -p|--push)
            DO_PUSH=true
            shift
            ;;
        --create-pr)
            DO_CREATE_PR=true
            shift
            ;;
        -d|--dry-run)
            DRY_RUN=true
            shift
            ;;
        -o|--order)
            ORDER="$2"
            shift 2
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            echo -e "${RED}Option inconnue : $1${NC}"
            show_help
            exit 1
            ;;
    esac
done

echo -e "${BLUE}${BOLD}======================================================${NC}"
echo -e "${BLUE}${BOLD}🎬 CinéLog - Consolidation des Pull Requests${NC}"
echo -e "${BLUE}${BOLD}======================================================${NC}"
echo -e "Branche de base : ${CYAN}${BASE_BRANCH}${NC}"
echo -e "Branche cible   : ${CYAN}${TARGET_BRANCH}${NC}"
echo -e "Mode Dry-Run    : $([ "$DRY_RUN" = true ] && echo -e "${YELLOW}OUI${NC}" || echo -e "${GREEN}NON${NC}")"
echo -e "Auto Push       : $([ "$DO_PUSH" = true ] && echo -e "${GREEN}OUI${NC}" || echo -e "${YELLOW}NON${NC}")"
echo -e "Créer/Maj PR    : $([ "$DO_CREATE_PR" = true ] && echo -e "${GREEN}OUI${NC}" || echo -e "${YELLOW}NON${NC}")"
echo ""

# Vérifier la présence de gh
if ! command -v gh &> /dev/null; then
    echo -e "${RED}❌ Erreur : 'gh' (GitHub CLI) n'est pas installé ou n'est pas dans le PATH.${NC}"
    exit 1
fi

# Récupérer les dernières données distantes
echo -e "${CYAN}🔄 Récupération des données distantes (git fetch origin)...${NC}"
git fetch origin "$BASE_BRANCH" --quiet

# Lister les PRs ouvertes ciblant la branche de base
echo -e "${CYAN}🔍 Récupération des Pull Requests ouvertes ciblant '${BASE_BRANCH}'...${NC}"
PRS_JSON=$(gh pr list --base "$BASE_BRANCH" --state open --json number,title,headRefName,url,author,isDraft --limit 100)

TOTAL_PRS=$(echo "$PRS_JSON" | grep -o '"number"' | wc -l || echo 0)

if [ "$TOTAL_PRS" -eq 0 ]; then
    echo -e "${YELLOW}⚠️ Aucune Pull Request ouverte trouvée ciblant '${BASE_BRANCH}'.${NC}"
    exit 0
fi

echo -e "${GREEN}✅ ${TOTAL_PRS} Pull Request(s) détectée(s).${NC}"
echo ""

# Sauvegarder la branche actuelle pour pouvoir y revenir si besoin
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")

# Préparer la branche consolidée
if [ "$DRY_RUN" = false ]; then
    echo -e "${CYAN}🌿 Initialisation de la branche '${TARGET_BRANCH}' depuis 'origin/${BASE_BRANCH}'...${NC}"
    git checkout -B "$TARGET_BRANCH" "origin/$BASE_BRANCH" --quiet
fi

# Tableaux de résultats
declare -a MERGED_PRS=()
declare -a CONFLICT_PRS=()
declare -a SKIPPED_PRS=()

# Extraction des PRs
# Si l'ordre est ascendant (défaut), on traite par numéro croissant
SORT_OPTION=""
if [ "$ORDER" = "asc" ]; then
    SORT_QUERY='sort_by(.number)'
else
    SORT_QUERY='sort_by(.number) | reverse'
fi

# Extraction des données PRs au format TSV (séparé par tabulation)
PARSED_PRS=$(gh pr list --base "$BASE_BRANCH" --state open --json number,title,headRefName,url,author,isDraft \
    --jq "$SORT_QUERY | .[] | \"\(.number)\t\(.headRefName)\t\(.author.login)\t\(.isDraft)\t\(.url)\t\(.title)\"")

echo -e "${BOLD}Démarrage de la fusion séquentielle :${NC}"
echo "------------------------------------------------------"

while IFS=$'\t' read -r PR_NUM PR_BRANCH PR_AUTHOR PR_DRAFT PR_URL PR_TITLE; do
    [ -z "$PR_NUM" ] && continue

    # Ignorer la branche consolidée elle-même si déjà ouverte en PR
    if [ "$PR_BRANCH" = "$TARGET_BRANCH" ]; then
        echo -e "⏭️  PR #${PR_NUM} correspond à la branche consolidée elle-même. Ignorée."
        continue
    fi

    echo -ne "👉 Traitement PR #${BOLD}${PR_NUM}${NC} (${PR_BRANCH}) - ${PR_TITLE} ... "

    # Fetch de la branche de la PR
    if ! git fetch origin "$PR_BRANCH":"refs/remotes/origin/$PR_BRANCH" --quiet 2>/dev/null; then
        echo -e "${YELLOW}[BRANCHE INTROUVABLE]${NC}"
        SKIPPED_PRS+=("$PR_NUM|$PR_TITLE|$PR_AUTHOR|$PR_URL|Branche introuvable sur le remote")
        continue
    fi

    if [ "$DRY_RUN" = true ]; then
        # Simulation avec git merge-tree
        MERGE_BASE=$(git merge-base HEAD "origin/$PR_BRANCH" 2>/dev/null || echo "")
        if [ -n "$MERGE_BASE" ] && git merge-tree "$MERGE_BASE" HEAD "origin/$PR_BRANCH" | grep -q "^+<<<<<<<"; then
            echo -e "${RED}[CONFLIT]${NC}"
            CONFLICT_PRS+=("$PR_NUM|$PR_TITLE|$PR_AUTHOR|$PR_URL|$PR_BRANCH")
        else
            echo -e "${GREEN}[OK - SIMULÉ]${NC}"
            MERGED_PRS+=("$PR_NUM|$PR_TITLE|$PR_AUTHOR|$PR_URL|$PR_BRANCH")
        fi
    else
        # Tentative de fusion réelle
        set +e
        MERGE_OUTPUT=$(git merge --no-ff "origin/$PR_BRANCH" -m "Merge PR #${PR_NUM}: ${PR_TITLE} (#${PR_NUM})" 2>&1)
        MERGE_STATUS=$?
        set -e

        if [ $MERGE_STATUS -eq 0 ]; then
            echo -e "${GREEN}[FUSIONNÉE]${NC}"
            MERGED_PRS+=("$PR_NUM|$PR_TITLE|$PR_AUTHOR|$PR_URL|$PR_BRANCH")
        else
            echo -e "${RED}[CONFLIT]${NC}"
            git merge --abort 2>/dev/null || git reset --hard HEAD --quiet
            CONFLICT_PRS+=("$PR_NUM|$PR_TITLE|$PR_AUTHOR|$PR_URL|$PR_BRANCH")
        fi
    fi
done <<< "$PARSED_PRS"

echo "------------------------------------------------------"
echo ""

# Affichage du bilan
MERGED_COUNT=${#MERGED_PRS[@]}
CONFLICT_COUNT=${#CONFLICT_PRS[@]}
SKIPPED_COUNT=${#SKIPPED_PRS[@]}

echo -e "${BOLD}📊 Résumé de la consolidation :${NC}"
echo -e "   🟢 PRs fusionnées : ${BOLD}${MERGED_COUNT}${NC}"
echo -e "   🔴 PRs en conflit : ${BOLD}${CONFLICT_COUNT}${NC}"
echo -e "   ⏭️  PRs ignorées   : ${BOLD}${SKIPPED_COUNT}${NC}"
echo ""

# Construction du corps de rapport Markdown
SUMMARY_MD="## 🎬 Consolidation des Pull Requests CinéLog

Cette branche regroupe l'intégration unifiée de toutes les Pull Requests ouvertes ciblant \`${BASE_BRANCH}\`.

### 📊 Bilan de l'intégration
- **Total PRs traitées** : $(( MERGED_COUNT + CONFLICT_COUNT + SKIPPED_COUNT ))
- **🟢 Fusionnées avec succès** : ${MERGED_COUNT}
- **🔴 Conflits détectés** : ${CONFLICT_COUNT}
- **⏭️ Ignorées / Erreurs** : ${SKIPPED_COUNT}

### 🟢 Pull Requests incluses (${MERGED_COUNT})
| PR | Titre | Auteur | Branche |
|---|---|---|---|
"

for item in "${MERGED_PRS[@]}"; do
    IFS='|' read -r num title author url branch <<< "$item"
    SUMMARY_MD+="| [#${num}](${url}) | ${title} | @${author} | \`${branch}\` |"$'\n'
done

if [ "$CONFLICT_COUNT" -gt 0 ]; then
    SUMMARY_MD+=$'\n'"### 🔴 Pull Requests avec conflits (${CONFLICT_COUNT})
Les PRs suivantes n'ont pas pu être intégrées automatiquement en raison de conflits de fusion avec les modifications précédentes :
| PR | Titre | Auteur | Branche |
|---|---|---|---|
"
    for item in "${CONFLICT_PRS[@]}"; do
        IFS='|' read -r num title author url branch <<< "$item"
        SUMMARY_MD+="| [#${num}](${url}) | ${title} | @${author} | \`${branch}\` |"$'\n'
    done
    SUMMARY_MD+=$'\n'"> [!WARNING]
> Pour intégrer ces PRs, rebasez-les sur la branche consolidée \`${TARGET_BRANCH}\` ou résolvez les conflits avec l'agent \`pr-conflict-guardian\`."$'\n'
fi

# Export pour GitHub Actions Step Summary si dans un runner
if [ -n "$GITHUB_STEP_SUMMARY" ]; then
    echo "$SUMMARY_MD" >> "$GITHUB_STEP_SUMMARY"
fi

# Sauvegarde locale du rapport
mkdir -p build/reports
echo "$SUMMARY_MD" > build/reports/pr-consolidation-summary.md
echo -e "📄 Rapport généré : ${CYAN}build/reports/pr-consolidation-summary.md${NC}"

# Push si demandé
if [ "$DO_PUSH" = true ] && [ "$DRY_RUN" = false ] && [ "$MERGED_COUNT" -gt 0 ]; then
    echo ""
    echo -e "${CYAN}🚀 Publication de la branche '${TARGET_BRANCH}' sur origin...${NC}"
    git push -u origin "$TARGET_BRANCH" --force
    echo -e "${GREEN}✅ Branche '${TARGET_BRANCH}' poussée avec succès.${NC}"
fi

# Création ou mise à jour de la PR
if [ "$DO_CREATE_PR" = true ] && [ "$DRY_RUN" = false ] && [ "$MERGED_COUNT" -gt 0 ]; then
    echo ""
    echo -e "${CYAN}📬 Création ou mise à jour de la Pull Request de consolidation...${NC}"
    
    PR_TITLE="[Consolidation] Fusion combinée de ${MERGED_COUNT} PRs vers ${BASE_BRANCH}"
    
    # Vérifier si une PR existe déjà pour cette branche
    EXISTING_PR=$(gh pr list --head "$TARGET_BRANCH" --base "$BASE_BRANCH" --json number --jq '.[0].number' 2>/dev/null || echo "")
    
    if [ -n "$EXISTING_PR" ]; then
        echo -e "Mise à jour de la PR existante #${EXISTING_PR}..."
        if gh pr edit "$EXISTING_PR" --title "$PR_TITLE" --body "$SUMMARY_MD" 2>/dev/null; then
            echo -e "${GREEN}✅ PR #${EXISTING_PR} mise à jour : $(gh pr view "$EXISTING_PR" --json url --jq .url 2>/dev/null)${NC}"
        else
            echo -e "${YELLOW}⚠️ Impossible de modifier la PR automatiquement (droits GITHUB_TOKEN restreints).${NC}"
        fi
    else
        echo -e "Création d'une nouvelle Pull Request..."
        set +e
        NEW_PR_URL=$(gh pr create --base "$BASE_BRANCH" --head "$TARGET_BRANCH" --title "$PR_TITLE" --body "$SUMMARY_MD" --label "consolidated-pr" 2>&1 || gh pr create --base "$BASE_BRANCH" --head "$TARGET_BRANCH" --title "$PR_TITLE" --body "$SUMMARY_MD" 2>&1)
        PR_STATUS=$?
        set -e
        if [ $PR_STATUS -eq 0 ]; then
            echo -e "${GREEN}✅ Pull Request créée avec succès : ${NEW_PR_URL}${NC}"
        else
            echo -e "${YELLOW}⚠️ La branche a été poussée, mais la PR n'a pas pu être créée automatiquement par GitHub Actions (permissions GITHUB_TOKEN restreintes).${NC}"
            echo -e "${CYAN}👉 Pour créer la PR manuellement : gh pr create --base ${BASE_BRANCH} --head ${TARGET_BRANCH}${NC}"
        fi
    fi
fi

echo ""
echo -e "${GREEN}${BOLD}🎉 Consolidation terminée avec succès !${NC}"
