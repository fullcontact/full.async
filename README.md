# full.async

A Clojure library that extends [core.async](https://github.com/clojure/core.async) 
with a number of convenience methods.

[![Clojars Project](http://clojars.org/fullcontact/full.async/latest-version.svg)](http://clojars.org/fullcontact/full.async)

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
(go-retry {:error-fn (fn [ex] (= 409 (.getStatus ex)))
           :exception HTTPException
           :retries 3
           :delay 5}
  (make-some-http-request))
```

The above method will invoke `make-some-http-request` in a `go` block. If it 
throws an exception of type `HTTPException` with a status code 409, `go-retry`
will wait 5 seconds and try invoking `make-some-http-request` again. If it still 
fails after 3 attempts or a different type of exception is thrown, it will get 
returned via the result channel.

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

`pmap>>` lets you apply a function to channel's output in parallel,
returning a new channel with results.


## License

Copyright (C) 2015 FullContact. Distributed under the Eclipse Public License, the same as Clojure.
