@echo off
set LOG=c:\Users\usuario\solare\_redeploy.log
set PROFILE=hackaton
set REGION=us-east-1
set STACK=solare
echo ==== REDEPLOY %DATE% %TIME% ==== > %LOG%

echo [a] Removendo stack solare (changeset REVIEW_IN_PROGRESS)... >> %LOG%
aws cloudformation delete-stack --stack-name %STACK% --profile %PROFILE% --region %REGION% >> %LOG% 2>&1
aws cloudformation wait stack-delete-complete --stack-name %STACK% --profile %PROFILE% --region %REGION% >> %LOG% 2>&1
echo (stack removida) >> %LOG%

echo [b] Aguardando a tabela solare-desenv sumir... >> %LOG%
aws dynamodb wait table-not-exists --table-name solare-desenv --profile %PROFILE% --region %REGION% >> %LOG% 2>&1
echo (tabela ausente) >> %LOG%

echo [c] cloudformation deploy... >> %LOG%
aws cloudformation deploy --template-file c:\Users\usuario\solare\infra\_packaged.yaml --stack-name %STACK% --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM --parameter-overrides Ambiente=desenv --profile %PROFILE% --region %REGION% >> %LOG% 2>&1
echo DEPLOY_EXIT=%ERRORLEVEL% >> %LOG%

echo [d] Outputs: >> %LOG%
aws cloudformation describe-stacks --stack-name %STACK% --query "Stacks[0].Outputs" --output json --profile %PROFILE% --region %REGION% >> %LOG% 2>&1
echo ==== FIM ==== >> %LOG%
