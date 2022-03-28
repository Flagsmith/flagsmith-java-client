# Flagsmith Java Example

This directory contains a basic Spring Boot application which utilises Flagsmith. To run the example application, you'll
need to go through the following steps:

1. Create an account, organisation and project on [Flagsmith](https://flagsmith.com)
2. Create a feature in the project called "secret_button"
3. Give the feature a value using the json editor as follows:

```json
{"colour": "#ababab"}
```


4. Set environment variable `FLAGSMITH_API_KEY` with your Flagsmith API key.
5. If you are facing issues with maven dependency for flagsmith, you may run 
`mvn clean install` on the flagsmith-java-client root directory (also ../). 
6. Run this project using mvn spring-boot:run.
7. The project is accessible at http://localhost:8080/

Now you can play around with the 'secret_button' feature in flagsmith, turn it on to show it and edit the colour in the
json value to edit the colour of the button. You can also identify as a given user and then update the settings for the
secret button feature for that user in the flagsmith interface to see the affect that has too. 