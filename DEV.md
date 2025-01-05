


```sh

./mill clean

./mill __.reformat

./mill tupson.__.test

./mill examples.runMain bla

git diff
git commit -am "msg"

# RELEASE
# bump publishVersion to x.y.z !!!
$VERSION="0.13.0"
git commit --allow-empty -am "Release $VERSION"
git tag -a $VERSION -m "Release $VERSION"
git push --atomic origin main --tags


# prepare for NEXT version
# bump publishVersion to x.y.z-SNAPSHOT
$VERSION="x.y.z-SNAPSHOT"
git commit -am"Bump version to $VERSION"


```


## TODO
- scala native




