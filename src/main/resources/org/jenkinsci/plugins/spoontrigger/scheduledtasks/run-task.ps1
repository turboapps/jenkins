[CmdletBinding()]
param
(
	[Parameter(Mandatory=$True,ValueFromPipeline=$True,ValueFromPipelineByPropertyName=$True,HelpMessage="Name of the scheduled task")]
	[string] $TaskName,
    [Parameter(Mandatory=$True,ValueFromPipeline=$True,ValueFromPipelineByPropertyName=$True,HelpMessage="Command to run")]
    [string] $Command,
    [Parameter(ValueFromPipeline=$True,ValueFromPipelineByPropertyName=$True,HelpMessage="Working directory")]
	[string] $WorkingDirectory
)

function Log-Status ($log) {
	Write-Output "# PowerShell => $log"
}

function Is-ScheduledTaskExist ($taskName) {
    return Get-ScheduledTask | Where-Object {$_.TaskName -like $taskName}
}

function Is-ScheduledTaskRunning ($taskName) {
	return Get-ScheduledTask | Where-Object {$_.TaskName -like $taskName -and $_.State -like "Running"}
}

$exitCode = 0
Try
{
    $taskExists = Is-ScheduledTaskExist $TaskName
    if($taskExists)
    {
	    Log-Status "Scheduled task '$TaskName' was left by previous job. Removing it now."
        Unregister-ScheduledTask $TaskName -Confirm:$False
    }

    $workingDirectoryToUse = $WorkingDirectory
    if(-not $workingDirectoryToUse)
    {
        $workingDirectoryToUse = Get-Location
    }

    $logFileName = "log$(Get-Date -Format yyyymmddThhmmss).txt"
    $logFilePath = [System.IO.Path]::Combine($workingDirectoryToUse, $logFileName)
    $logStream = New-Object System.IO.FileStream $logFilePath, 'OpenOrCreate', 'Read', 'Write'
    $reader = New-Object System.IO.StreamReader $logStream

    function Forward-Logs()
    {
        $logs = $reader.ReadToEnd()
        if($logs)
        {
            Write-Output $logs
        }
    }

    try
    {
        $encodedCommand = [Convert]::ToBase64String([System.Text.Encoding]::Unicode.GetBytes("$Command >> $logFilePath 2>&1"))
        $taskAction = New-ScheduledTaskAction -Execute PowerShell.exe -Argument "-WindowStyle Hidden -EncodedCommand $encodedCommand" -WorkingDirectory $workingDirectoryToUse
        Log-Status "Registering scheduled task '$TaskName'"
        Register-ScheduledTask -Action $taskAction -TaskName $TaskName | Out-Null
        
        Log-Status "Starting scheduled task '$TaskName'"
        Start-ScheduledTask $TaskName

        $isTaskRunning = $True
        while ($isTaskRunning)
        {
            Forward-Logs

	        Start-Sleep -Seconds 5

            $isTaskRunning = Is-ScheduledTaskRunning $TaskName
        }

        Forward-Logs
    }
    finally
    {
        $reader.Close()
    }

    Log-Status "Scheduled task '$TaskName' finished"


    Remove-Item $logFilePath
}
Catch
{
    Write-Error $_.Exception.Message
    $exitCode = 1
}
Finally
{
    $taskExists = Is-ScheduledTaskExist $TaskName
    if($taskExists)
    {
	    Log-Status "Removing scheduled task '$TaskName'"
        Unregister-ScheduledTask $TaskName -Confirm:$False
    }
}

Exit $exitCode