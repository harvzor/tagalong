# How to compare

```bash
exiftool -G0:1 -a -u {{path}}
```

Then create a diff:

```bash
delta --no-gitconfig --hunk-header-style omit --keep-plus-minus-markers --paging never before.txt after.txt
```
