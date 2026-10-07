@echo off
setlocal
set LOG=c:\Users\usuario\solare\_deploy.log
set PROFILE=hackaton
set REGION=us-east-1
set BUCKET=solare-deploy-811205581925-us-east-1
set STACK=solare
echo ==== DEPLOY SOLARE %DATE% %TIME% ==== > %LOG%

echo [1/4] Garantindo bucket de artefatos... >> %LOG%
aws s3 mb s3://%BUCKET% --profile %PROFILE% --region %REGION% >> %LOG% 2>&1
echo (bucket mb exit=%ERRORLEVEL% - ok se ja existir) >> %LOG%

echo [2/4] cloudformation package... >> %LOG%
aws cloudformation package --template-file c:\Users\usuario\solare\infra\template-cli.yaml --s3-bucket %BUCKET% --output-template-file c:\Users\usuario\solare\infra\_packaged.yaml --profile %PROFILE% --region %REGION% >> %LOG% 2>&1
echo PACKAGE_EXIT=%ERRORLEVEL% >> %LOG%
if not %ERRORLEVEL%==0 (echo ABORTANDO: package falhou >> %LOG% & goto :fim)

echo [3/4] cloudformation deploy... >> %LOG%
aws cloudformation deploy --template-file c:\Users\usuario\solare\infra\_packaged.yaml --stack-name %STACK% --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM --parameter-overrides Ambiente=desenv --profile %PROFILE% --region %REGION% >> %LOG% 2>&1
echo DEPLOY_EXIT=%ERRORLEVEL% >> %LOG%

echo [4/4] Outputs da stack... >> %LOG%
aws cloudformation describe-stacks --stack-name %STACK% --query "Stacks[0].Outputs" --output json --profile %PROFILE% --region %REGION% >> %LOG% 2>&1

:fim
echo ==== FIM ==== >> %LOG%
endlocal
