


```sh

./mill clean

./mill -i mill.scalalib.scalafmt/

./mill tupson.__.test

./mill examples.runMain bla

git diff
git commit -am "msg"

# RELEASE
$VERSION="0.13.0"
git commit --allow-empty -am "Release $VERSION"
git tag -a $VERSION -m "Release $VERSION"
git push --atomic origin main --tags


```


## TODO
- scala native




