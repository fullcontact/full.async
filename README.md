# full.async

A Clojure and ClojureScript library that extends [core.async](https://github.com/clojure/core.async)
with a number of convenience functions.

[![Clojars Project](https://img.shields.io/clojars/v/fullcontact/full.async.svg)](https://clojars.org/fullcontact/full.async)
[![Build Status](https://travis-ci.org/fullcontact/full.async.svg?branch=master)](https://travis-ci.org/fullcontact/full.async)

## Exception Handling

Exception handling is an area for which core.async doesn't have extensive
support out of the box. If something within a `go` block throws an exception, it
will be logged but the block will simply return `nil`, hiding any information
about the actual exception. You could wrap the body of each `go` block within an
exception handler but that's  not very convenient. `full.async` provides a set of
helper functions and macros for dealing with exceptions in a simple manner:

* `go-try`: equivalent of `go` but catches any exceptions thrown and returns via
the resulting channel
* `<?`, `alts?`, `<??`: equivalents of `<!`, `alts!` and `<!!` but if the value
is an exception, it will get thrown

## Retry Logic

Sometimes it may be necessary to retry certain things when they fail,
especially in distributed systems. The `go-retry` macro lets you achieve that,
for example:

```clojure
(go-retry
  {:should-retry-fn (fn [res] (= 409 (:status res)))
   :retries 3
   :delay 5}
  (<? (make-some-http-request>)))
```

The above method will invoke `(<? (make-some-http-request>)` in a `go` block. If
it returns `{:status 409}`, `go-retry` will wait 5 seconds and try invoking
`(<? (make-some-http-request>)` again. If it still fails after 3 attempts, the
last will be returned via the result channel.

## Sequences & Collections

Channels by themselves are quite similar to sequences however converting between
them may sometimes be cumbersome. `full.async` provides a set of convenience
methods for this:

* `<<!`, `<<?`: takes all items from the channel and returns them as a collection.
Must be used within a `go` block.
* `<<!!`, `<<??`: takes all items from the channel and returns as a lazy
sequence. Returns immediately.
* `<!*`, `<?*`, `<!!*`, `<??*` takes one item from each input channel and
returns them in a single collection. Results have the same ordering as input
channels.

## Parallel Processing

`pmap>>` lets you apply an async function (ie. function that returns channel) to
channel's output with a specified parallelism, returning a new channel with
results.

## Debouncing

`debounce>>` takes input channel and only passes through values at specified
rate, dropping the rest.

## Conventions

For readability of code, `full.async` follows these conventions:
* Async operators that throw exceptions use `?` in place of `!`, for example
throwing counterpart of `<!` is `<?`.
* Functions that return channel that will contain zero or one value (typically
result of `go` blocks) are sufixed with `>`. Similarly operators that expect
zero/single value channel as input are prefixed with `<` (for example `<?`).
* Functions that return channel that will contain zero to many values are
sufixed with `>>`. Similarly operators that expect zero to many value channel as
input are prefixed with `<<` (for example `<<?`).

## License

Copyright (C) 2016 FullContact. Distributed under the Eclipse Public License, the same as Clojure.
