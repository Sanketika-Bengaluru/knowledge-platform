#!/bin/bash

echo "Fixing collection conversion issues in unified service..."

cd /Users/sanketikam4/November/knowledge-platform/knowlg-unified-service

# Fix 1: Replace all getOrElse with getOrDefault for Java Maps
echo "1. Fixing getOrElse -> getOrDefault..."
find app -name "*.scala" -exec sed -i '' 's/\.getOrElse(/\.getOrDefault(/g' {} +

# Fix 2: Fix JavaConverters references 
echo "2. Fixing JavaConverters references..."
find app -name "*.scala" -exec sed -i '' 's/JavaConverters\.//' {} +

# Fix 3: Fix collection method calls on Java collections
echo "3. Fixing Java collection method calls..."
# Replace .toList on Java Lists with .asScala.toList
find app -name "*.scala" -exec sed -i '' 's/\.toList\.foreach/.asScala.toList.foreach/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\.toList\.flatMap/.asScala.toList.flatMap/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\.toList\.map/.asScala.toList.map/g' {} +

# Fix 4: Fix foreach on Java collections  
find app -name "*.scala" -exec sed -i '' 's/\.foreach(/.asScala.foreach(/g' {} +

# Fix 5: Fix filter operations on Java collections
find app -name "*.scala" -exec sed -i '' 's/\.filter(/.asScala.filter(/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\.filterNot(/.asScala.filterNot(/g' {} +

# Fix 6: Fix map operations on Java collections
find app -name "*.scala" -exec sed -i '' 's/\.map(/.asScala.map(/g' {} +

# Fix 7: Fix sortBy on Java collections
find app -name "*.scala" -exec sed -i '' 's/\.sortBy(/.asScala.sortBy(/g' {} +

# Fix 8: Fix toMap on Java Maps 
find app -name "*.scala" -exec sed -i '' 's/\.toMap\.getOrDefault/.asScala.toMap.getOrElse/g' {} +
find app -name "*.scala" -exec sed -i '' 's/\.toMap\.get/.asScala.toMap.get/g' {} +

# Fix 9: Replace length with size for Java Lists
find app -name "*.scala" -exec sed -i '' 's/\.length/.size/g' {} +

# Fix 10: Fix contains vs containsKey
find app -name "*.scala" -exec sed -i '' 's/\.contains(/.containsKey(/g' {} +

echo "Collection conversion fixes applied!"
echo "Note: Some fixes may need manual review for context-specific cases."