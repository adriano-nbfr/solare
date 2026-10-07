# AWS Profile Enforcement

This project uses the AWS profile hackaton (us-east-1).

When running any AWS CLI command or generating any AWS-related code:

- Always use --profile hackaton with AWS CLI commands
- Always set AWS_PROFILE=hackaton when suggesting environment variables
- When generating AWS SDK code, configure it to use the hackaton profile
- When creating SAM or CloudFormation deployment commands, include --profile hackaton
- Never use a default profile or any other named profile