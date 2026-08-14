#!/usr/bin/env bash
# ==============================================================================
# CineLog - PR Conflict Checker
# Détecte de manière préventive les conflits de fusion entre la branche courante
# et la branche de base cible (par défaut origin/main).
# ==============================================================================

set -uo pipefail

TARGET_BRANCH="${1:-origin/main}"
CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo 'HEAD')"

echo "🔍 [PR Conflict Guard] Vérification des conflits entre '${CURRENT_BRANCH}' et '${TARGET_BRANCH}'..."

# S'assurer que la référence cible existe
if git rev-parse --verify "${TARGET_BRANCH}" >/dev/null 2>&1; then
    TARGET_REF="${TARGET_BRANCH}"
elif git rev-parse --verify "origin/${TARGET_BRANCH}" >/dev/null 2>&1; then
    TARGET_REF="origin/${TARGET_BRANCH}"
else
    echo "⚠️ Branche cible introuvable : ${TARGET_BRANCH}. Tentative de fetch..."
    git fetch origin "${TARGET_BRANCH}" 2>/dev/null || true
    TARGET_REF="${TARGET_BRANCH}"
fi

# Simulation de fusion avec git merge-tree --write-tree
MERGE_OUTPUT=$(git merge-tree --write-tree HEAD "${TARGET_REF}" 2>&1)
EXIT_CODE=$?

if [ $EXIT_CODE -ne 0 ] || echo "${MERGE_OUTPUT}" | grep -E "(CONFLICT|changed in both|<<<<<<<)" >/dev/null; then
    echo ""
    echo "========================================================================"
    echo "🚨 CONFLIT DE FUSION DÉTECTÉ !"
    echo "========================================================================"
    echo "La branche '${CURRENT_BRANCH}' ne peut pas être fusionnée automatiquement dans '${TARGET_REF}'."
    echo ""
    echo "📁 Détails des conflits :"
    echo "${MERGE_OUTPUT}" | grep -E "(CONFLICT|Auto-merging)" || echo "${MERGE_OUTPUT}"
    echo ""
    echo "🛠️ Procédure recommandée pour résoudre ce conflit :"
    echo "   1. Récupérer les dernières modifications de la branche principale :"
    echo "      git fetch origin main"
    echo "   2. Rebaser votre branche de travail :"
    echo "      git rebase origin/main"
    echo "   3. Résoudre les conflits manuellement dans les fichiers indiqués."
    echo "   4. Valider le rebase et pousser les modifications :"
    echo "      git add <fichiers-résolus>"
    echo "      git rebase --continue"
    echo "      git push --force-with-lease origin ${CURRENT_BRANCH}"
    echo "   5. Vous pouvez également invoquer l'agent 'pr-conflict-guardian' dans Antigravity."
    echo "========================================================================"
    exit 1
else
    echo "✅ [PR Conflict Guard] Aucun conflit détecté avec '${TARGET_REF}' ! La branche peut être fusionnée proprement."
    exit 0
fi
