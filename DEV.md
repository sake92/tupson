


```sh

./mill clean

./mill __.reformat

./mill tupson.__.test

./mill examples.runMain bla

git diff
git commit -am "msg"

# powershell..

$VERSION="0.10.0"
git commit --allow-empty -m "Release $VERSION"
git tag -a $VERSION -m "Release $VERSION"
git push  --atomic origin main $VERSION

```


## TODO
- 




