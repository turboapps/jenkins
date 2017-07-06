[CmdletBinding()]
param
(	
    [Parameter(Mandatory=$True,ValueFromPipeline=$False,ValueFromPipelineByPropertyName=$False,HelpMessage="Build script path")]
    [string] $buildScript,
	
    [Parameter(Mandatory=$True,ValueFromPipeline=$False,ValueFromPipelineByPropertyName=$False,HelpMessage="The name of the vm machine to use for the build")]
    [string] $machine,

	[Parameter(Mandatory=$True,ValueFromPipeline=$False,ValueFromPipelineByPropertyName=$False,HelpMessage="Path to install script.")]
    [string] $installScript,
	
	[Parameter(Mandatory=$True,ValueFromPipeline=$False,ValueFromPipelineByPropertyName=$False,HelpMessage="Path to xstudio.")]
    [string] $studioPath,
	
	[Parameter(Mandatory=$True,ValueFromPipeline=$False,ValueFromPipelineByPropertyName=$False,HelpMessage="Path to studio license file.")]
    [string] $studioLicensePath,
	
	[Parameter(Mandatory=$False,ValueFromPipeline=$False,ValueFromPipelineByPropertyName=$False,HelpMessage="Path to preinstall script.")]
    [string] $preinstallScript,
	
	[Parameter(Mandatory=$False,ValueFromPipeline=$False,ValueFromPipelineByPropertyName=$False,HelpMessage="Path to postsnapshot script.")]
    [string] $postSnapshotScript,
	
	[Parameter(Mandatory=$False,ValueFromPipeline=$False,ValueFromPipelineByPropertyName=$False,HelpMessage="Path to directory to be copied to shared vm folder.")]
    [string] $mountDir,
	
    [Parameter(Mandatory=$False,ValueFromPipeline=$False,ValueFromPipelineByPropertyName=$False,HelpMessage="Path to virtualbox directory (where main exe is")]
    [string] $virtualboxDir = "C:\Program Files\Oracle\VirtualBox"
)


#Workaround for issues with Jenkins plugin calling empty arguments
If ($preinstallScript -eq " ") { $preinstallScript = "" }
If ($postSnapshotScript -eq " ") { $postSnapshotScript = "" }
If ($mountDir -eq " ") { $mountDir = "" }
If ($virtualboxDir -eq " ") { $virtualboxDir = "" }

#Force allows to overwrite
Copy-Item "$studioPath" ".\share\tools\xstudio.exe"
Copy-Item "$studioLicensePath" ".\share\tools\license.txt"
Copy-Item "$buildScript" ".\share\tools\buildScript.ps1"

Copy-Item "$installScript" ".\share\install"
If($preinstallScript) { Copy-Item "$preinstallScript" ".\share\install\preInstall.ps1" }
If($postSnapshotScript) { Copy-Item "$postSnapshotScript" ".\share\install\postSnapshot.ps1" }
If($mountDir) { Copy-Item "$mountDir" ".\share" -Recurse }

#Restore virtual machine snapshot
& "$virtualboxDir\VBoxManage.exe" snapshot $machine restore "turboBuild"
& "$virtualboxDir\VBoxManage.exe" sharedfolder add $machine --name turboBuild --hostpath (Get-Item ".\share").FullName

#Extract image name, namespace and version
$imageContent = Get-Content .\image.txt
$imageContent -match "(?<namespace>[a-zA-Z-_]*)/(?<appname>[a-zA-Z-_]*):(?<version>[0-9.]*)" | Out-Null

$imageNamespace = $Matches['namespace']
$imageAppName = $Matches['appname']
$imageVersion = $Matches['version']

#Run virtual machine to create the svm
& "$virtualboxDir\VBoxManage.exe" startvm $machine

while (!(Test-Path ".\share\buildDone"))
{
	Sleep 5
}
#Turn virtual machine off
& "$virtualboxDir\VBoxManage.exe" controlvm $machine poweroff
#Restore virtual machine snapshot
& "$virtualboxDir\VBoxManage.exe" snapshot $machine restore "turboBuild"

Copy-Item ".\share\output\image.svm" ".\$($imageNamespace)_$($imageAppName)_$($imageVersion).svm"
Copy-Item ".\share\output\buildlog.txt" ".\buildlog_$($imageNamespace)_$($imageAppName)_$($imageVersion).txt"

Remove-Item ".\share" -Recurse
Remove-Item ".\image.txt"

Get-Content ".\buildlog_$($imageNamespace)_$($imageAppName)_$($imageVersion).txt"

Exit 1