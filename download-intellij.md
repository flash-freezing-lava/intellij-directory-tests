# How to find which intellij artifacts are needed
The general strategy is to download all artifacts to a temporary directory
and there search the missing class using `fd '^CLASS_NAME\.class$'`.

Select the `<td>` element of the wanted version of `com.jetbrains.intellij.platform` in your browser and copy the outer html to `table.html`.

```nu
open table.html | from xml --allow-dtd | to json | jq --raw-output '.content | map(.attributes | .href) | .[]' | rg '\.jar$' | lines | each { |it| wget $it }
```

```sh
for j in *.jar; do unzip -q -d "${j%%.jar}" $j; done
```

