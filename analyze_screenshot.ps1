Add-Type -AssemblyName System.Drawing
$bmp = [System.Drawing.Bitmap]::FromFile('C:\Users\skane\VisualStudioCodeProjects\TableTennisScore\device-screenshot.png')
$w = $bmp.Width
$h = $bmp.Height

$left = [ordered]@{ minX = 99999; minY = 99999; maxX = -1; maxY = -1 }
$right = [ordered]@{ minX = 99999; minY = 99999; maxX = -1; maxY = -1 }
$set = [ordered]@{ minX = 99999; minY = 99999; maxX = -1; maxY = -1 }

for ($y = 0; $y -lt [Math]::Min($h, 360); $y++) {
    for ($x = 0; $x -lt $w; $x++) {
        $c = $bmp.GetPixel($x, $y)

        if ($c.R -gt 140 -and $c.G -gt 140 -and $c.B -lt 90) {
            if ($x -gt 850 -and $x -lt 1356) {
                if ($x -lt $left.minX) { $left.minX = $x }
                if ($y -lt $left.minY) { $left.minY = $y }
                if ($x -gt $left.maxX) { $left.maxX = $x }
                if ($y -gt $left.maxY) { $left.maxY = $y }
            }
            elseif ($x -gt 1356 -and $x -lt 1860) {
                if ($x -lt $right.minX) { $right.minX = $x }
                if ($y -lt $right.minY) { $right.minY = $y }
                if ($x -gt $right.maxX) { $right.maxX = $x }
                if ($y -gt $right.maxY) { $right.maxY = $y }
            }
        }

        # tighter set-digit window near the middle top
        if ($x -gt 1240 -and $x -lt 1470 -and $y -gt 70 -and $y -lt 260 -and $c.R -gt 180 -and $c.G -gt 180 -and $c.B -gt 180) {
            if ($x -lt $set.minX) { $set.minX = $x }
            if ($y -lt $set.minY) { $set.minY = $y }
            if ($x -gt $set.maxX) { $set.maxX = $x }
            if ($y -gt $set.maxY) { $set.maxY = $y }
        }
    }
}

$bmp.Dispose()
Write-Output ("LEFT=" + ($left | ConvertTo-Json -Compress))
Write-Output ("RIGHT=" + ($right | ConvertTo-Json -Compress))
Write-Output ("SET=" + ($set | ConvertTo-Json -Compress))


