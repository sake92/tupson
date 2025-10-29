---
title: How Tos
description: Tupson How Tos
pagination:
  enabled: false
---

# {{ page.title }}

## Why not implicit config


Circe for example is using an `implicit` configuration, where you can tell it to map camelCase to snake_case etc.  
Tupson deliberately avoids this in order to simplify things.  
It is really hard to find which implicit config is being applied where, and you need to test your codecs.. meh

Benefits of Tupson's simplistic approach:
- your code is easy for "grep" / Ctrl+F
- no mismatch between serialized version and your code
- your internal/domain models are separate from JSON, as they should be
- mapping between models is explicit
  
