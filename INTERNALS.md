

Here are some of (mostly controversial and/or poor?) design choices I made here.


# Validation

## Validation baked into JSON parser itself
At a first glance, this is odd, right?  
Separation of concerns etc, right?

The thing is that I don't *really* care.  
What I *do care about* is getting:
- a simple to use interface, i.e calling a bunch of `check`s in a constructor
- making invalid objects not possible to construct
- enriching validation errors with JsonPath paths


## Validation is based on exceptions


## Nulls in validation handling

This one is a bit tricky.  
I didnt want to fiddle with reflection, or implement something like Json Schema.

One issue I bumped into is that validation is fail-early.  
Consider this example:
```
case class Person(name: String, address: Address) {
    validate(name, _.length > 3)
}
case class Address(street: String) {
    validate(street, _.length > 5)
}
```
Let's say we are parsing this string:
```
{
    "name": "A",
    "address": {
        "street" : "bla"
    }
}
```
The parser parses JSON correctly and starts to build these 2 objects.  
The `name` field *by itself* is just a string, no validation attached to it, so all good.  
Next we parse `address`, and its construction will fail, since validation fails.  
Here, normally, we could stop and throw an exception.  

But that would **only report** the `$.address.street` validation error.  
We want to see ALL validation errors!  
That's why we set `null` for address itself, and try to construct the `Person` object.  

This limitation/quirk/bad-choice, however you call it.. means that you **shouldnt do anything in the constructor apart from validating the arguments**.  
In example above, if you had this:
```
case class Person(name: String, address: Address) {
    validate(name, _.length > 3)
    println(address.street)
}
```
where `name` is valid but `address` is not, you'd get a nullpointer exception.

----
Alternatives?

## validators as implicits
I could've used something like https://index.scala-lang.org/yakivy/dupin  
and have validators "on side".  
But I really, really, dont like having invalid object instances laying around.. 

## validation as implicits made with a macro

*I think* this would be the best approach:

```scala
case class Person(name: String, address: Address) {
    validate(name, _.length > 3)
}
```
where  macro would take the `validate` function and make an implicit `Validator[Person]`.

This way you would get 2 benefits:
- exception thrown at construction time
- validation at JSON-construction time, with all errors collected

