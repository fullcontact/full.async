# full.async

A Clojure library that extends [core.async](https://github.com/clojure/core.async) 
with a number of convenience methods.

## Exception Handling

Exception handling is an area for which core.async doesn't have an extensive 
support out of the box. If something within a `go` block throws an exception, it 
will be logged but the block will simply return `nil` hiding any infromation 
about the actual exception. You could wrap body of each `go` block within an 
exception handler but that's  not very convenient. Full.async provide a set of 
helper functions and macros for dealing  with exceptions in a simple manner:

* `go-try`: equivalent of `go` but catches any exceptions thrown and returns via
resulting channel
* `<?`, `alts?`, `<??`: equivalents of `<!`, `alts?` and `<??` but if the value 
is an exception, it will get thrown


## Retry Logic

Sometimes it may be neccessary to retry certains things when they fail, 
especially in distributed systems. `go-retry` macro lets you achieve that, for
example:

```clojure
(go-retry {:error-fn (fn [ex] (= 409 (.getStatus ex)))
           :exception HTTPException
           :retries 3
           :delay 5}
  (make-some-http-request))
```

The above method will invoke `make-some-http-request` in a `go` block. If it 
throws an exception of a type `HTTPException` with a status code 409, `go-retry`
will wait 5 seconds and try invoking `make-some-http-request` again. If it still 
fails after 3 retries or any other type of exception is thrown, it will get 
return via the result channel.

## Sequences

Channels by themselves are very similiar to sequences however converting between
them may sometimes be cumbersome. `full.async` provides a set of convenience 
methods:

* `<<!`, `<<?`: takes all items from the channel and returns as collection. Must
be used within a `go` block. 
* `<<!!`, `<<??`: takes all items from the channel and returns as a lazy 
sequence. Will block the execution thread when no items are available (yet) 
and channel is not closed.
* `<!all`, `<?all`: takes all items from a collection of channels and returns
in a single collection. Note that the ordering of results is not deterministic.

## Parrallel Processing

`pmap-chan>>` lets you apply a function to channel's output in parallel, 
returning a new channel with results.

