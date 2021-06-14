## Replication of AsyncHttpClient Bug

In continuance with the discussion around the issue [#3527](https://github.com/http4s/http4s/issues/3527), this
repo includes a test case which produces this error only when the AsyncHttpClient is used.

It boils down to performing this twice on the same `Response[IO]` object:

```scala
response.bodyText.compile.foldMonoid map {
      r => log.info(s"resp: $r")
    }
```

Please see `com.example.asynchttpclienterror.AsyncHttpClientErrorSpec` for a detailed example.
