#!/bin/sh -ex

# https://maven.apache.org/maven-ci-friendly.html
REVISION=$(date +%Y.%m).${GITHUB_RUN_ID?}

AWS_ENDPOINT=http://localhost:4566 ./mvnw verify -B -Drevision=${REVISION?}

ln -fs target/awscat-${REVISION?}.jar awscat.jar

cat > setup.py << EOF
import setuptools
setuptools.setup(name="awscat", version="${REVISION?}", scripts=["awscat.jar"])
EOF

zip awscat.zip awscat.jar setup.py

# create github release
response_code=$(curl -sv -o output -w "%{response_code}" -H"Authorization: Token ${GITHUB_TOKEN?}" https://api.github.com/repos/percent25/awscat/releases -d @-) << EOF
{
  "tag_name": "${REVISION?}",
  "target_commitish": "${GITHUB_SHA?}"
}
EOF
cat output

# upload github release binary
if [ $response_code -lt 400 ]; then
  release_id=$(jq .id output)
  response_code=$(curl -sv -o output -w "%{response_code}" -H"Authorization: Token ${GITHUB_TOKEN?}" -H"Content-Type: application/octet-stream" "https://uploads.github.com/repos/percent25/awscat/releases/${release_id?}/assets?name=awscat.zip" --data-binary @awscat.zip)
  cat output
  if [ $response_code -lt 400 ]; then
    exit 0
  fi
fi

exit 1
