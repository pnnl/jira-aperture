# Aperture for JIRA
A JIRA Add-on that adds custom workflows and automation hooks for provisioning services within other Atlassian tools 
like Confluence, Bitbucket, and Crucible/Fisheye based on project keys. 

# Atlassian SDK
This project makes use of the  Atlassian SDK found here - https://developer.atlassian.com/display/DOCS/Introduction+to+the+Atlassian+Plugin+SDK

Some common commands to get going. Otherwise, Please have OpenJDK 8 and Maven 3 for getting a development environment 
for this add-on going  as smoothly as possible.
  
* atlas-run   -- installs this plugin into the product and starts it on localhost
* atlas-debug -- same as atlas-run, but allows a debugger to attach at port 5005
* atlas-cli   -- after atlas-run or atlas-debug, opens a Maven command line window:
                 - 'pi' reinstalls the plugin into the running product instance
* atlas-help  -- prints description for all commands in the SDK


