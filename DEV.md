


```sh

brew install sake92/tap/deder

# clear local Deder state if needed
deder shutdown
rm -rf .deder

deder exec -t runMvnApp fmt
deder exec -t test

# for local dev/test
deder exec -t publishLocal

deder exec -t runMain -m examples parse

# RELEASE
./scripts/release.sh 0.18.1

```




