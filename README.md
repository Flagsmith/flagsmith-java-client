<img width="100%" src="https://raw.githubusercontent.com/SolidStateGroup/bullet-train-frontend/master/hero.png"/>

# Bullet Train SDK for Java

> Bullet Train allows you to manage feature flags and remote config across multiple projects, environments and organisations.

The SDK for Android and Java applications for [https://bullet-train.io/](https://bullet-train.io/).

## Getting Started

## Quick Setup

The client library is available from the Central Maven Repository and can be added to your project by many tools:

### Maven

Add following dependencies to your project in `pom.xml`
```xml
<dependency>
  <groupId>com.solidstategroup</groupId>
  <artifactId>bullet-train-client</artifactId>
  <version>1.6</version>
</dependency>
```

### Gradle
```groovy
implementation 'com.solidstategroup:bullet-train-client:1.6'
```

## Usage
**Retrieving feature flags for your project**

**For full documentation visit [https://docs.bullet-train.io](https://docs.bullet-train.io)**

Sign Up and create an account at [https://bullet-train.io/](https://www.bullet-train.io/)

In your application initialise the BulletTrain client with your API key:

```Java
BulletTrainClient bulletClient = BulletTrainClient.newBuilder()
                .setApiKey("YOUR_ENV_API_KEY")
                .build();
```

To check if a feature flag exists and is enabled:

```Java
boolean featureEnabled = bulletClient.hasFeatureFlag("my_test_feature");
if (featureEnabled) {
    // run the code that executes the enabled feature
} else {
    // run the code that doesn't include the feature
}
```

To get configuration value for a feature flag:

```Java
String myRemoteConfig = bulletClient.getFeatureFlagValue("my_test_feature");
if (myRemoteConfig != null) {    
    // run the code that uses the remote config value
} else {
    // run the code that doesn't depend on the remote config value
}
```

**Identifying users**

Identifying users allows you to target specific users from the [Bullet Train dashboard](https://www.bullet-train.io/).

To check if feature a exists for given a user context:

```Java
User user = new User();
user.setIdentifier("bullet_train_sample_user");
boolean featureEnabled = bulletClient.hasFeatureFlag("my_test_feature", user);
if (featureEnabled) {
    // run the code that executes the enabled feature for a given user
} else {
    // run the code that doesn't include the feature
}
```

To get the configuration value of a feature flag for a given user context:

```Java
String myRemoteConfig = bulletClient.getFeatureFlagValue("my_test_feature", user);
if (myRemoteConfig != null) {    
    // run the code that uses the remote config value
} else {
    // run the code tbat doesn't depend on the remote config value
}
```

To get user traits for a given user context:

```Java
List<Trait> userTraits = bulletClient.getTraits(user)
if (userTraits != null && userTraits) {    
    // run the code that expects the user traits
} else {
    // run the code that doesn't depend on user traits
}
```

To get a user trait for a given user context and specific key:

```Java
Trait userTrait = bulletClient.getTrait(user, "cookies_key");
if (userTrait != null) {    
    // run the code that uses the user trait
} else {
    // run the code that doesn't depend on the user trait
}
```

Or get the user traits for a given user context and specific keys:

```Java
 List<Trait> userTraits = bulletClient.getTraits(user, "cookies_key", "other_trait");
if (userTraits != null) {    
    // run the code that uses the user traits
} else {
    // run the code doesn't depend on user traits
}
```

To update the value for user traits for a given user context and specific keys:

```Java
 Trait userTrait = bulletClient.getTrait(user, "cookies_key");
if (userTrait != null) {    
    // update the value for a user trait
    userTrait.setValue("new value");
    Trait updated = bulletClient.updateTrait(user, userTrait);
} else {
    // run the code that doesn't depend on the user trait
}
```

**Flags and Traits**

Or get flags and traits for a user in a single call:

```Java
FlagsAndTraits userFlagsAndTraits = bulletClient.getUserFlagsAndTraits(user);
// get traits
List<Trait> traits = bulletClient.getTraits(userFlagsAndTraits, "cookies_key");
// or get a flag value
String featureFlagValue = bulletClient.getFeatureFlagValue("font_size", userFlagsAndTraits);
// or get flag enabled
boolean enabled = bulletClient.hasFeatureFlag("hero", userFlagsAndTraits);

// see above examples on how to evaluate flags and traits
```

## Override default configuration

By default, the client uses a default configuration. You can override the configuration as follows:

override just the default API URI with your own
```Java
BulletTrainClient bulletClient = BulletTrainClient.newBuilder()
                .setApiKey("YOUR_ENV_API_KEY")
                .withApiUrl("http://yoururl.com")
                .build();
```

override the full configuration with your own
```Java
BulletTrainClient bulletClient  = BulletTrainClient.newBuilder()
            .setApiKey("YOUR_ENV_API_KEY")
            .withConfiguration(BulletTrainConfig.newBuilder()
                    .baseURI("http://yoururl.com")
                    .connectTimeout(200)
                    .writeTimeout(5000)
                    .readTimeout(5000)
                    .build())
            .build();

```

## Contributing

Please read [CONTRIBUTING.md](https://gist.github.com/kyle-ssg/c36a03aebe492e45cbd3eefb21cb0486) for details on our Code of Conduct, and the process for submitting Pull Requests to us.

## Getting Help

If you encounter a bug, or have a feature request, we would like to hear about it. But, before you submit an issue, please search the [existing issues](https://github.com/BulletTrainHQ/bullet-train-java-client/issues) in order to prevent duplicates. 

## Get in touch

If you have any questions about our projects you can email us at <a href="mailto:projects@solidstategroup.com">projects@solidstategroup.com</a>.

