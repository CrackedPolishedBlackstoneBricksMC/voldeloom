set -euo pipefail

if ! command -v git
then
  echo "a 'git' executable could not be found"
  exit 1
fi

mkdir -p ./work
cd ./work

rm -rf ./forge
mkdir -p ./forge
cd ./forge

echo "|-> pwd: $PWD - cloning Forge..."
git clone "https://github.com/MinecraftForge/MinecraftForge" .
# latest commit on branch "1.7.10" as of Feb 20, 2023 (not like that will really change, tbh)
echo "|-> checking out 1.7.10..."
git checkout 9274e4fe435cb415099a8216c1b42235f185443e

echo "|-> deleting git metadata..."
cd ../
rm -rf ./forge/.git

if ! command -v zip
then
  echo "a 'zip' executable could not be found (git bash?); manually zip the contents of $PWD/forge and put it at $PWD/hilarious-funny-forge.zip, please"
  exit 0
fi

echo "|-> creating zip..."
zip -r hilarious-funny-forge.zip ./forge

echo "done :)"