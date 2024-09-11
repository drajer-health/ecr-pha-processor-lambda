# ecr-pha-processor-lambda

S3 Trigger to push files to PHA / Health Care Centers. 

### Prerequisites:

1.  Java 17 or Higher
2.  AWS SDK - STS or Eclipse
3.  AWS Account
4.  Maven 3.3.x
5.  GIT

## Clone the Repository

Clone the repository using the below command in command prompt

`git clone https://github.com/drajer-health/ecr-pha-processor-lambda.git

## Create Build:

Import Project as Maven Project Build:

Navigate to `ecr-pha-processor-lambda` directory  `..../` and run Maven build to build lambda jar file.

```

$ mvn clean install
```

This will generate a war file under target/ecr-pha-processor-lambda-1.0.0.jar.

## AWS Lambda

### Deploy eCR PHA Processor Lambda:

Login to your AWS Account

1.  Click on Services then select Lambda
    
2.  Click on Create Function
    
3.  Select "Author from Scratch" option
    
4.  Enter:
    

```
Function Name: ecr-PHA-Processor-lambda
Runtime: Java 17
Permissions: Create a new role with basic Lambda permissions or select your organization specific security
```
5. Click on "Create Function"


## At this point Lambda function would be created, navigate to the newly created function and configure the lambda function and environment variable.

1. Go to the newly created Role.

2. Under `Permissions` tab click on `Create inline Policy`

3. Click on `{ } JSON` tab and ad the following security policy. Replace the `S3-BUCKET-NAME` with your S3 name.

```
	{
 {
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "ListObjectsInBucketS3",
            "Effect": "Allow",
            "Action": [
                "s3:GetObjectVersion",
                "s3:GetBucketLocation",
                "s3:GetObject",
                "s3:PutObject",
                "s3:PutObjectAcl"
            ],
            "Resource": "arn:aws:s3:::S3-BUCKET-NAME/*"
        }
    ]
}
```

4. Click on button `Next` 

5. Enter policy name `ecrPhaProcessorLamdaPolcy`

6. Click on `Create policy`

7. Come back to your AWS Lambda Function and navigate to `Configuration` tab.

8. Go to the `General Configuration` and click on `Edit` button. Increase the Timeout to minimum 10 minute. 

9. Click on `Save`

10.  Under the "Code" tab select "Upload from"

8. Select .zip or .jar file option.

9. Click upload and navigate to your local workspace target folder and select ecr-pha-processor-lambda-1.0.0.jar and click "Save".

10. Click on "Edit" on "Runtime Settings".

11. Enter below value for Handler
    

```
com.drajer.ecr.pha.processor.PHAProcessorLambdaFunctionHandler::handleRequest

```
12.  Click "Save"
 
### Lambda Configuration
To process the file from the S3 bucket, lambda function needs to be configured to process from the specified folder. Add the ***Environment Variable*** to the lambda function specifying the S3 bucket folder name.

1.  Click on "Configuration" tab and then "Environment Variables"
    
2.  Click on "Edit" to add new environment variable
    
3.  Click on "Add new environment variable"
    
4.  Enter
    

|Environment Variable| Value |
|--|--|
|HTTP_POST_URL	  | <- HTTP end point reference to eicr responder module ->  |

eg: http://<<EICR RESPONDER SERVER>>:<<PORT>>/eicrresponder/api/receiveeicrrdata

### SQS Queue
Choose the SQS queue and click `Create Queue` 

1. Select `Standard` and Enter the Name for the Queue as `eg: fhir-ecr1-pha-processor-sqs-queue`
   
3. Enter 10 minutes as Visibility timeout
   
4. Server-Side encryption as `disabled`
   
5. Access Policy `Advanced`
   
6. Make neccessary changes to below and copy as in-line policy

```
{
  "Version": "2012-10-17",
  "Id": "__default_policy_ID",
  "Statement": [
    {
      "Sid": "__owner_statement",
      "Effect": "Allow",
      "Principal": {
        "Service": "s3.amazonaws.com"
      },
      "Action": "SQS:SendMessage",
      "Resource": "arn:aws:sqs:us-east-1:<<AWS_ACCOUNT_INFO>>:<<QUEUE_NAME (from the SQS Queue Step 1)>>",
      "Condition": {
        "StringEquals": {
          "aws:SourceAccount": "<<AWS ACCOUNT INFO>>"
        },
        "ArnLike": {
          "aws:SourceArn": "arn:aws:s3:::<<S3 BUCKET NAME>>"
        }
      }
    }
  ]
}
```

6. Click Save


### S3 Event Notification

1. Go to S3 bucket and to Properties Tab

2. Scroll down to `Event Notification` and Click `Create event Notification`

3. Enter Name `eg: rr-fhir-event`

4. Enter Suffix as `RR_FHIR.xml`

5. Event Types as `All object create events`

6. Destination as `SQS queue`

7. Specify SQS queue 
    Enter SQS queque 
    
    `arn:aws:<<SQS NAME (from the SQS Queue Step 1)>>`

8. Click `Save Changes` 



### Lambda Trigger
Lambda function needs to be triggered, for this we need to add and configure the trigger. Follow the following steps to add the trigger to your lambda function.
1. Go to you Lambda function

2. Click on `Add trigger`

3. From the `Trigger configuration` drop down select
    `SQS` option

4. From the `SQS queque (from the SQS Queue Step 1)` drop down select your SQS that this lambda function will listen.

6. Click Add.


### At this point the Lambda function is created and configured to listen to the S3 Bucket.
