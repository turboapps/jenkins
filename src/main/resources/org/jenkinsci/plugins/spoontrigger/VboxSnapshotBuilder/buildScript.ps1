Get-Date
"Preinstallation script `n"
if(Test-Path("x:\install\preInstall.ps1"))
{
	x:\install\preInstall.ps1
}
else
{
	"There is no preinstallation script."
}

Get-Date
"Taking Before snapshot `n"
x:\tools\xstudio.exe /before /beforepath c:\output\snapshot

Get-Date
"Installing application `n"
x:\install\install.ps1

Get-Date
"Taking after snapshot `n"
x:\tools\xstudio.exe /after /beforepath c:\output\snapshot /o c:\output

Get-Date
"Post snapshot script `n"
if(Test-Path("x:\install\postSnapshot.ps1"))
{
	x:\install\postSnapshot.ps1
}
else
{
	"There is no post snapshot script."
}

Get-Date
"Building image `n"
x:\tools\xstudio.exe c:\output\snapshot.xappl /o x:\output\image.svm /component /uncompressed /l x:\tools\license.txt
Get-Date

New-Item X:\buildDone -type file | Out-Null