#!/usr/bin/bash

set -euxo pipefail

git fetch --depth=1 origin gh-pages
git checkout gh-pages
cp {.,}github/badges/branches.svg
if [[ `git status --porcelain | grep "^.. github/"` ]]; then
  git config --global user.name 'Monalisa Octocat'
  git config --global user.email '41898282+github-actions[bot]@users.noreply.github.com'
  git commit -a -m "Autogenerated JaCoCo coverage badge"
  git push
fi
git checkout -
