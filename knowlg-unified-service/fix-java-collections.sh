#!/bin/bash

echo "🔧 Fixing Java/Scala collection interoperability issues..."

cd /Users/sanketikam4/November/knowledge-platform/knowlg-unified-service

# Revert all changes first
find app -name "*.scala" -exec sed -i '' 's/\.getOrDefault(/\.getOrElse(/g' {} +

# Fix Java Map instances specifically (these have java.util.Map type)
echo "Fixing java.util.Map getOrElse -> getOrDefault..."
find app -name "*.scala" -exec sed -i '' 's/\([^.]*body\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*request\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*filters\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*data\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*content\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*metadata\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*category\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*question\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*questionSet\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*itemset\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*assessmentItem\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*framework\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*term\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*channel\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*license\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*asset\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*app\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*categoryInstance\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*categoryDefinition\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*commentList\)\.getOrElse(/\1.getOrDefault(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\([^.]*reqMap\)\.getOrElse(/\1.getOrDefault(/g' {} +

# Fix containsKey -> contains for Maps 
echo "Fixing containsKey -> contains..."
find app -name "*.scala" -exec sed -i '' 's/\.containsKey(/\.contains(/g' {} +

# Remove unnecessary asScala calls
echo "Removing unnecessary asScala calls..."
find app -name "*.scala" -exec sed -i '' 's/\.asScala\.asScala\.asScala\.toMap/.toMap/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\.asScala\.toMap/.toMap/g' {} +
find app -name "*.scala" -exec sed -i '' 's/customHeaders\.asScala\./customHeaders./g' {} +

echo "✅ Java/Scala collection fixes applied"