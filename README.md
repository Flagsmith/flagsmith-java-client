[![Download](https://img.shields.io/maven-central/v/com.flagsmith/flagsmith-java-client)](https://mvnrepository.com/artifact/com.flagsmith/flagsmith-java-client)

# Flagsmith Java SDK

> Flagsmith allows you to manage feature flags and remote config across multiple projects, environments and organisations.

This SDK enables Android and Java applications to integrate with [Flagsmith](https://www.flagsmith.com/).

## Adding to your project

For full documentation visit [https://docs.flagsmith.com/clients/server-side](https://docs.flagsmith.com/clients/server-side).

## Experimentation events

Enable events on the configuration, then record exposures and custom events:

```java
FlagsmithClient flagsmith = FlagsmithClient.newBuilder()
    .setApiKey(System.getenv("FLAGSMITH_ENVIRONMENT_KEY"))
    .withConfiguration(FlagsmithConfig.newBuilder()
        .withEnableEvents(true)
        .build())
    .build();

// Records one $flag_exposure event when the identity is enrolled in a running experiment.
BaseFlag flag = flagsmith.getExperimentFlag("checkout_cta", "user-123");

flagsmith.trackEvent("purchase", "user-123");
```

Events are buffered and sent in batches, on a timer and when the buffer fills. A batch that fails
on a retryable error (408, 429, 502, 503, 504 or a network error) is tried up to 3 times in
total, with backoff, and then kept for the next timed flush. Any other error drops it, and a 401
or 403 stops sending until the client is re-created.

`flushEvents()` sends what is buffered now. A short-lived process, such as a serverless function
or a CLI command, must call `close()` before it exits: it sends the remaining events and waits
for them, within a bound derived from the HTTP client's timeouts. Otherwise buffered events are
lost.

`getDroppedEventCount()` returns how many events were dropped: when the buffer overflowed, on a
non-retryable error, when the events API rejected them, after a 401 or 403, or on close.

## Contributing

Please read [CONTRIBUTING.md](https://gist.github.com/kyle-ssg/c36a03aebe492e45cbd3eefb21cb0486) for details on our code of conduct, and the process for submitting pull requests

## Getting Help

If you encounter a bug or feature request we would like to hear about it. Before you submit an issue please search existing issues in order to prevent duplicates.

## Get in touch

If you have any questions about our projects, email us at [support@flagsmith.com](mailto:support@flagsmith.com).

## Code Style formatting

We use Google Java Style for code formatting. To install it, see the instructions below.

### VS Code

1. Install the [Checkstyle for Java](https://marketplace.visualstudio.com/items?itemName=shengchen.vscode-checkstyle) extension.
2. Use the `java-google-style.xml` file in the `docs` folder as the Checkstyle configuration file.

### IntelliJ

To add GoogleStyle formatting in IntelliJ go to `Preferences -> Editor - Code Style -> Java`. From Schema `Import Schema` and select file `docs/java-google-style.xml`
Once added, you will be able to reformat code using GoogleStyle. This can be triggered from the Code menu or with the `Ctrl + Alt + L` shortcut (default).
## Useful links

[Website](https://www.flagsmith.com/)

[Documentation](https://docs.flagsmith.com/)
