---
title: Tutorials
description: Tupson Tutorials
pagination:
  enabled: false
---

# {{ page.title }}

{%

set tutorials = [
    { label: "Quickstart", url: "/tutorials/quickstart.html" },
    { label: "Writing", url: "/tutorials/writing.html" },
    { label: "Parsing", url: "/tutorials/parsing.html" },
    { label: "Typesafe Config", url: "/tutorials/parsing-config.html" },
    { label: "sttp client4", url: "/tutorials/sttp.html" }
]

%}

{% for tut in tutorials %}- [{{ tut.label }}]({{ tut.url}})
{% endfor %}

