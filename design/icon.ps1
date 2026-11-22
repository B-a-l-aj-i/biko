param([string]$Src, [string]$Res)
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System; using System.Drawing; using System.Drawing.Imaging; using System.Drawing.Drawing2D;
public static class Ico {
  // White-to-alpha: result over white looks identical to the source.
  public static Bitmap Unwhite(string path, out Rectangle box) { var src = new Bitmap(path);
    var b = new Bitmap(src.Width, src.Height, PixelFormat.Format32bppArgb);
    int minX=int.MaxValue,minY=int.MaxValue,maxX=-1,maxY=-1;
    for (int y=0;y<src.Height;y++) for (int x=0;x<src.Width;x++) {
      var c = src.GetPixel(x,y);
      int m = Math.Min(c.R, Math.Min(c.G, c.B));
      int a = 255 - m;
      if (a < 8) { b.SetPixel(x,y,Color.FromArgb(0,0,0,0)); continue; }
      Func<int,int> un = v => Math.Max(0, Math.Min(255, 255 - (255 - v) * 255 / a));
      b.SetPixel(x,y,Color.FromArgb(a, un(c.R), un(c.G), un(c.B)));
      if (a > 40) { minX=Math.Min(minX,x); minY=Math.Min(minY,y); maxX=Math.Max(maxX,x); maxY=Math.Max(maxY,y); }
    }
    box = Rectangle.FromLTRB(minX,minY,maxX+1,maxY+1);
    return b;
  }
  // Content centred on a size x size canvas, its height = frac of the canvas.
  public static Bitmap Place(Bitmap art, Rectangle box, int size, double frac, Color bg) {
    var o = new Bitmap(size,size,PixelFormat.Format32bppArgb);
    using (var g = Graphics.FromImage(o)) {
      g.Clear(bg);
      g.InterpolationMode = InterpolationMode.HighQualityBicubic;
      g.PixelOffsetMode = PixelOffsetMode.HighQuality;
      g.CompositingQuality = CompositingQuality.HighQuality;
      double s = size * frac / Math.Max(box.Width, box.Height);
      int w = (int)Math.Round(box.Width*s), h = (int)Math.Round(box.Height*s);
      var ia = new ImageAttributes(); ia.SetWrapMode(WrapMode.TileFlipXY);
      g.DrawImage(art, new Rectangle((size-w)/2,(size-h)/2,w,h), box.X,box.Y,box.Width,box.Height, GraphicsUnit.Pixel, ia);
    }
    return o;
  }
}
"@

$box = [System.Drawing.Rectangle]::Empty
$art = [Ico]::Unwhite($Src, [ref]$box)
"content box: $box"
$dens = @{ mdpi = 1.0; hdpi = 1.5; xhdpi = 2.0; xxhdpi = 3.0; xxxhdpi = 4.0 }
foreach ($d in $dens.Keys) {
  $dir = Join-Path $Res "mipmap-$d"; New-Item -ItemType Directory -Force $dir | Out-Null
  $fg = [Ico]::Place($art, $box, [int](108 * $dens[$d]), 0.56, [System.Drawing.Color]::Transparent)
  $fg.Save((Join-Path $dir "ic_launcher_foreground.png"), [System.Drawing.Imaging.ImageFormat]::Png)
}
$prev = [Ico]::Place($art, $box, 512, 0.56, [System.Drawing.Color]::White)
$prev.Save((Join-Path (Split-Path $PSCommandPath) "preview.png"), [System.Drawing.Imaging.ImageFormat]::Png)
"done"
