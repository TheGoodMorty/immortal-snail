Add-Type -AssemblyName System.Drawing

$cubes = @(
    [pscustomobject]@{ name="body_bottom";     ox=-2; oy=0; oz=-2; w=4; h=2; d=4; r=184; g=145; b=90 }
    [pscustomobject]@{ name="body_top";        ox=-2; oy=2; oz=-2; w=4; h=1; d=4; r=122; g=92;  b=54 }
    [pscustomobject]@{ name="shell_base";      ox=-1; oy=3; oz=-1; w=2; h=1; d=2; r=201; g=152; b=96 }
    [pscustomobject]@{ name="shell_spiral_br"; ox=1;  oy=3; oz=1;  w=1; h=1; d=1; r=165; g=120; b=70 }
    [pscustomobject]@{ name="shell_spiral_bl"; ox=-2; oy=3; oz=1;  w=1; h=1; d=1; r=165; g=120; b=70 }
    [pscustomobject]@{ name="shell_spiral_fl"; ox=-2; oy=3; oz=-2; w=1; h=1; d=1; r=165; g=120; b=70 }
    [pscustomobject]@{ name="eye_l";           ox=1;  oy=2; oz=-1; w=1; h=1; d=1; r=40;  g=30;  b=20 }
    [pscustomobject]@{ name="eye_r";           ox=1;  oy=2; oz=0;  w=1; h=1; d=1; r=40;  g=30;  b=20 }
    [pscustomobject]@{ name="antenna_l";       ox=1;  oy=3; oz=-1; w=1; h=1; d=1; r=150; g=110; b=60 }
    [pscustomobject]@{ name="antenna_r";       ox=1;  oy=3; oz=0;  w=1; h=1; d=1; r=150; g=110; b=60 }
)

$scale = 16
$cellW = 220
$cellH = 200
$margin = 30
$canvasW = 4 * $cellW + 60
$canvasH = 2 * $cellH + 60

$bmp = New-Object System.Drawing.Bitmap($canvasW, $canvasH)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::FromArgb(245, 245, 245))
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

$fontSmall = New-Object System.Drawing.Font("Arial", 11, [System.Drawing.FontStyle]::Regular)
$fontBold  = New-Object System.Drawing.Font("Arial", 13, [System.Drawing.FontStyle]::Bold)
$fontTitle = New-Object System.Drawing.Font("Arial", 16, [System.Drawing.FontStyle]::Bold)
$brush     = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::Black)
$redBrush  = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(180, 180, 50, 50))

# Title
$g.DrawString("Snail Model Reference - 4x4x4 pixel envelope", $fontTitle, $brush, $margin, 8)

# Helper: draw a cube's visible face for a given orthographic view
function Draw-FrontFace($g, $cube, $scale, $ox, $oy) {
    # Looking down +X axis: see the +X face. Map (y, z) -> (x_screen, y_screen).
    # We want z on horizontal axis, y on vertical (y up = screen up).
    $x0 = $cube.oz * $scale + $ox
    $y0 = -($cube.oy + $cube.h) * $scale + $oy
    $x1 = ($cube.oz + $cube.d) * $scale + $ox
    $y1 = -$cube.oy * $scale + $oy
    $br = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, $cube.r, $cube.g, $cube.b))
    $g.FillRectangle($br, $x0, $y0, $x1 - $x0, $y1 - $y0)
    $g.DrawRectangle([System.Drawing.Pens]::Black, $x0, $y0, $x1 - $x0, $y1 - $y0)
    $br.Dispose()
}
function Draw-SideFace($g, $cube, $scale, $ox, $oy) {
    # Looking down -Z axis: see the -Z face. Map (x, y) -> (x_screen, y_screen).
    $x0 = $cube.ox * $scale + $ox
    $y0 = -($cube.oy + $cube.h) * $scale + $oy
    $x1 = ($cube.ox + $cube.w) * $scale + $ox
    $y1 = -$cube.oy * $scale + $oy
    $br = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, $cube.r, $cube.g, $cube.b))
    $g.FillRectangle($br, $x0, $y0, $x1 - $x0, $y1 - $y0)
    $g.DrawRectangle([System.Drawing.Pens]::Black, $x0, $y0, $x1 - $x0, $y1 - $y0)
    $br.Dispose()
}
function Draw-TopFace($g, $cube, $scale, $ox, $oy) {
    # Looking down -Y axis: see the +Y (top) face. Map (x, z) -> (x_screen, y_screen).
    $x0 = $cube.ox * $scale + $ox
    $y0 = -($cube.oz + $cube.d) * $scale + $oy
    $x1 = ($cube.ox + $cube.w) * $scale + $ox
    $y1 = -$cube.oz * $scale + $oy
    $br = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, $cube.r, $cube.g, $cube.b))
    $g.FillRectangle($br, $x0, $y0, $x1 - $x0, $y1 - $y0)
    $g.DrawRectangle([System.Drawing.Pens]::Black, $x0, $y0, $x1 - $x0, $y1 - $y0)
    $br.Dispose()
}

# Iso projection (rotate Y by 45, then X by 30)
function Iso-Project([double]$x, [double]$y, [double]$z, [double]$scale) {
    $a = [math]::PI / 4
    $x1 = $x * [math]::Cos($a) + $z * [math]::Sin($a)
    $z1 = -$x * [math]::Sin($a) + $z * [math]::Cos($a)
    $b = [math]::PI / 6
    $y1 = $y * [math]::Cos($b) - $z1 * [math]::Sin($b)
    $z2 = $y * [math]::Sin($b) + $z1 * [math]::Cos($b)
    return @{
        sx = [double]$x1 * $scale
        sy = -[double]$y1 * $scale
        depth = [double]$z2
    }
}

function Draw-CubeIso($g, $cube, $scale, $ox, $oy) {
    # Compute 8 corners (x, y, z)
    $corners = @(
        [pscustomobject]@{ x=$cube.ox;          y=$cube.oy;          z=$cube.oz }
        [pscustomobject]@{ x=$cube.ox+$cube.w;  y=$cube.oy;          z=$cube.oz }
        [pscustomobject]@{ x=$cube.ox+$cube.w;  y=$cube.oy+$cube.h;  z=$cube.oz }
        [pscustomobject]@{ x=$cube.ox;          y=$cube.oy+$cube.h;  z=$cube.oz }
        [pscustomobject]@{ x=$cube.ox;          y=$cube.oy;          z=$cube.oz+$cube.d }
        [pscustomobject]@{ x=$cube.ox+$cube.w;  y=$cube.oy;          z=$cube.oz+$cube.d }
        [pscustomobject]@{ x=$cube.ox+$cube.w;  y=$cube.oy+$cube.h;  z=$cube.oz+$cube.d }
        [pscustomobject]@{ x=$cube.ox;          y=$cube.oy+$cube.h;  z=$cube.oz+$cube.d }
    )

    # Face definitions: list of 4 corner indices (CCW viewed from outside), and a shade multiplier
    $faces = @(
        @{ name="top";    idxs=@(2,3,7,6); shade=1.15 }
        @{ name="right";  idxs=@(1,2,6,5); shade=0.95 }
        @{ name="front";  idxs=@(0,1,2,3); shade=1.00 }
        @{ name="left";   idxs=@(0,3,7,4); shade=0.85 }
        @{ name="back";   idxs=@(5,6,7,4); shade=0.75 }
        @{ name="bottom"; idxs=@(0,1,5,4); shade=0.65 }
    )

    foreach ($face in $faces) {
        $pts = New-Object System.Drawing.PointF[] 4
        for ($i = 0; $i -lt 4; $i++) {
            $ci = $face.idxs[$i]
            $cn = $corners[$ci]
            $proj = Iso-Project $cn.x $cn.y $cn.z $scale
            $pts[$i] = New-Object System.Drawing.PointF ([float]($proj.sx + $ox), [float]($proj.sy + $oy))
        }
        $r = [int][math]::Min(255, $cube.r * $face.shade)
        $gg = [int][math]::Min(255, $cube.g * $face.shade)
        $b = [int][math]::Min(255, $cube.b * $face.shade)
        $faceBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, $r, $gg, $b))
        $g.FillPolygon($faceBrush, $pts)
        $g.DrawPolygon([System.Drawing.Pens]::Black, $pts)
        $faceBrush.Dispose()
    }
}

# Iso depth (mean of corners) for back-to-front sorting
function Iso-Depth($cube) {
    $p = Iso-Project ($cube.ox + $cube.w / 2.0) ($cube.oy + $cube.h / 2.0) ($cube.oz + $cube.d / 2.0) 1
    return $p.depth
}

# === Row 0: front (+X), back (-X), right (+Z), left (-Z) ===
$row = 0
$views = @(
    @{ name="Front view  (looking -X, snail faces you)"; proj="front" }
    @{ name="Back view   (looking +X, snail faces away)"; proj="back"  }
    @{ name="Right view  (looking -Z)";                    proj="right" }
    @{ name="Left view   (looking +Z)";                    proj="left"  }
)
for ($i = 0; $i -lt 4; $i++) {
    $col = $i
    $ox = $col * $cellW + $margin + $cellW/2
    $oy = $row * $cellH + $margin + $cellH/2

    $g.DrawString($views[$i].name, $fontBold, $brush, $col * $cellW + $margin, $oy - 90)

    foreach ($cube in $cubes) {
        switch ($views[$i].proj) {
            "front" { Draw-FrontFace $g $cube $scale $ox $oy }
            "back"  {
                # Flip z (the visible axis): o.z' = -(z + d)
                $flipped = [pscustomobject]@{ ox=$cube.ox; oy=$cube.oy; oz=-$cube.oz - $cube.d; w=$cube.w; h=$cube.h; d=$cube.d; r=$cube.r; g=$cube.g; b=$cube.b }
                Draw-FrontFace $g $flipped $scale $ox $oy
            }
            "right" { Draw-SideFace $g $cube $scale $ox $oy }
            "left"  {
                $flipped = [pscustomobject]@{ ox=-$cube.ox - $cube.w; oy=$cube.oy; oz=$cube.oz; w=$cube.w; h=$cube.h; d=$cube.d; r=$cube.r; g=$cube.g; b=$cube.b }
                Draw-SideFace $g $flipped $scale $ox $oy
            }
        }
    }
    # Envelope outline
    $envPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(180, 200, 50, 50))
    $envPen.DashStyle = [System.Drawing.Drawing2D.DashStyle]::Dash
    switch ($views[$i].proj) {
        "front" {
            $g.DrawRectangle($envPen, -2*$scale + $ox, -4*$scale + $oy, 4*$scale, 4*$scale)
        }
        "back"  {
            $g.DrawRectangle($envPen, -2*$scale + $ox, -4*$scale + $oy, 4*$scale, 4*$scale)
        }
        "right" {
            $g.DrawRectangle($envPen, -2*$scale + $ox, -4*$scale + $oy, 4*$scale, 4*$scale)
        }
        "left"  {
            $g.DrawRectangle($envPen, -2*$scale + $ox, -4*$scale + $oy, 4*$scale, 4*$scale)
        }
    }
    $envPen.Dispose()
}

# === Row 1: top, bottom, isometric, legend ===
$row = 1

# Top view
$ox = $margin + $cellW/2
$oy = $row * $cellH + $margin + $cellH/2
$g.DrawString("Top view    (looking -Y, top of shell)", $fontBold, $brush, $margin, $oy - 90)
foreach ($cube in $cubes) { Draw-TopFace $g $cube $scale $ox $oy }
$envPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(180, 200, 50, 50))
$envPen.DashStyle = [System.Drawing.Drawing2D.DashStyle]::Dash
$g.DrawRectangle($envPen, -2*$scale + $ox, -2*$scale + $oy, 4*$scale, 4*$scale)
$envPen.Dispose()

# Bottom view
$ox = $cellW + $margin + $cellW/2
$oy = $row * $cellH + $margin + $cellH/2
$g.DrawString("Bottom view (looking +Y, underside)", $fontBold, $brush, $cellW + $margin, $oy - 90)
foreach ($cube in $cubes) { Draw-TopFace $g $cube $scale $ox $oy }
$envPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(180, 200, 50, 50))
$envPen.DashStyle = [System.Drawing.Drawing2D.DashStyle]::Dash
$g.DrawRectangle($envPen, -2*$scale + $ox, -2*$scale + $oy, 4*$scale, 4*$scale)
$envPen.Dispose()

# Isometric view
$ox = 2 * $cellW + $margin + $cellW/2
$oy = $row * $cellH + $margin + $cellH/2
$g.DrawString("Isometric", $fontBold, $brush, 2*$cellW + $margin, $oy - 90)
$sorted = $cubes | Sort-Object -Property @{Expression={ Iso-Depth $_ }; Ascending=$true}
foreach ($cube in $sorted) { Draw-CubeIso $g $cube $scale $ox $oy }

# Legend
$ox = 3 * $cellW + $margin + 20
$oy = $row * $cellH + $margin + 30
$g.DrawString("Cube legend", $fontBold, $brush, 3*$cellW + $margin, $oy)
$ly = $oy + 25
foreach ($cube in $cubes) {
    $br = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, $cube.r, $cube.g, $cube.b))
    $g.FillRectangle($br, $ox, $ly, 18, 18)
    $g.DrawRectangle([System.Drawing.Pens]::Black, $ox, $ly, 18, 18)
    $br.Dispose()
    $g.DrawString($cube.name, $fontSmall, $brush, $ox + 24, $ly + 2)
    $ly += 24
}
$ly += 12
$g.DrawString("Red dashed box = 4x4x4 envelope", $fontSmall, $redBrush, 3*$cellW + $margin, $ly)
$ly += 18
$g.DrawString("1 pixel = 1/16 block = 0.0625 blocks", $fontSmall, $brush, 3*$cellW + $margin, $ly)
$ly += 18
$g.DrawString("Total envelope: 0.25 x 0.25 x 0.25 blocks", $fontSmall, $brush, 3*$cellW + $margin, $ly)

$outDir = "src\main\resources\assets\immortalsnail\textures\entity"
if (!(Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
$bmp.Save("$outDir\snail_model_reference.png", [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
$g.Dispose()
$fontSmall.Dispose(); $fontBold.Dispose(); $fontTitle.Dispose()
$brush.Dispose(); $redBrush.Dispose()

Write-Output "Wrote snail_model_reference.png"
