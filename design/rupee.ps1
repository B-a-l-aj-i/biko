# Turns the money bag's "$" into a "₹" in the same crayon look.
# The "$" is covered with the bag's own fill texture, then the "₹" is drawn as a
# mask filled with ink pixels sampled from the bag's dark outline.
# Usage: .\rupee.ps1 -Src icon-source-dollar.png -Out icon-source.png [-Font "Segoe UI"]
param([string]$Src, [string]$Out, [string]$Font = "unused", [float]$Size = 160, [float]$Angle = -6, [int]$Seed = 7)
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System; using System.Collections.Generic; using System.Drawing; using System.Drawing.Imaging; using System.Drawing.Drawing2D; using System.Drawing.Text;
public static class Rup {
  static double Lum(Color c) { return 0.299*c.R + 0.587*c.G + 0.114*c.B; }
  public static string Run(string src, string outPath, string font, float size, float angle, int seed) {
    var bmp = new Bitmap(src);
    var img = new Bitmap(bmp.Width, bmp.Height, PixelFormat.Format32bppArgb);
    using (var g = Graphics.FromImage(img)) g.DrawImage(bmp, 0, 0, bmp.Width, bmp.Height);
    var rnd = new Random(seed);

    // Where the "$" is, and a patch of plain bag fill beside it (576px source).
    var dollar = Rectangle.FromLTRB(242, 207, 338, 402);
    var fillPatch = Rectangle.FromLTRB(344, 296, 392, 396);
    var inkPatch = Rectangle.FromLTRB(190, 418, 390, 445);

    var fill = new List<Color>(); var fillL = new List<double>();
    for (int y = fillPatch.Top; y < fillPatch.Bottom; y++) for (int x = fillPatch.Left; x < fillPatch.Right; x++) { var c = img.GetPixel(x, y); fill.Add(c); fillL.Add(Lum(c)); }
    fillL.Sort();
    double fillLow = fillL[(int)(fillL.Count * 0.03)];
    // Ink: the darkest third of the bottom outline band.
    var band = new List<Color>();
    for (int y = inkPatch.Top; y < inkPatch.Bottom; y++) for (int x = inkPatch.Left; x < inkPatch.Right; x++) band.Add(img.GetPixel(x, y));
    band.Sort((p, q) => Lum(p).CompareTo(Lum(q)));
    var ink = band.GetRange(0, band.Count / 3);
    double inkL = Lum(ink[ink.Count / 2]);

    // 1. Cover the "$" with real bag texture: pixels darker than the fill (plus a
    //    3px margin) take the fill patch's pixels, tiled so its crayon streaks
    //    carry on, nudged to the same brightness, with a soft edge so no block shows.
    double fillMed = fillL[fillL.Count / 2];
    var hard = new float[img.Width, img.Height];
    double aroundSum = 0; int aroundN = 0;
    for (int y = dollar.Top; y < dollar.Bottom; y++) for (int x = dollar.Left; x < dollar.Right; x++) {
      double l = Lum(img.GetPixel(x, y));
      if (l < fillMed - 14) { for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) hard[x + dx, y + dy] = 1f; }
      else { aroundSum += l; aroundN++; }
    }
    double fillMean = 0; foreach (var l in fillL) fillMean += l; fillMean /= fillL.Count;
    double shift = aroundN > 0 ? aroundSum / aroundN - fillMean : 0;
    // Fill each masked run down a column by blending the clean pixels just above
    // and below it, plus the fill patch's grain (its pixels minus their mean),
    // so the crayon's vertical streaks run straight through with no seams.
    int covered = 0;
    var orig = (Bitmap)img.Clone();
    double mr = 0, mg = 0, mb = 0;
    for (int y = fillPatch.Top; y < fillPatch.Bottom; y++) for (int x = fillPatch.Left; x < fillPatch.Right; x++) { var c = orig.GetPixel(x, y); mr += c.R; mg += c.G; mb += c.B; }
    int pn = fillPatch.Width * fillPatch.Height; mr /= pn; mg /= pn; mb /= pn;
    Func<int, int, int> pong = (v, n) => { int p = ((v % (2 * n)) + 2 * n) % (2 * n); return p < n ? p : 2 * n - 1 - p; };
    Func<int, int, Color> avgAt = (x, y) => {
      int r = 0, gg = 0, bb = 0, n = 0;
      for (int dx = -2; dx <= 2; dx++) { var c = orig.GetPixel(x + dx, y); r += c.R; gg += c.G; bb += c.B; n++; }
      return Color.FromArgb(r / n, gg / n, bb / n);
    };
    for (int x = dollar.Left - 3; x < dollar.Right + 3; x++) {
      int y = dollar.Top - 3;
      while (y < dollar.Bottom + 3) {
        if (hard[x, y] == 0f) { y++; continue; }
        int y0 = y; while (y < dollar.Bottom + 3 && hard[x, y] > 0f) y++;
        int y1 = y - 1;
        var top = avgAt(x, y0 - 2); var bot = avgAt(x, y1 + 2);
        for (int yy = y0; yy <= y1; yy++) {
          double t = (yy - y0 + 1.0) / (y1 - y0 + 2.0);
          var s = orig.GetPixel(fillPatch.Left + pong(x - dollar.Left, fillPatch.Width), fillPatch.Top + pong(yy - dollar.Top, fillPatch.Height));
          Func<double, double, double, int> mix = (a, b, grain) => Math.Max(0, Math.Min(255, (int)(a + (b - a) * t + grain * 0.9)));
          img.SetPixel(x, yy, Color.FromArgb(255, mix(top.R, bot.R, s.R - mr), mix(top.G, bot.G, s.G - mg), mix(top.B, bot.B, s.B - mb)));
          covered++;
        }
      }
    }

    // 2. Draw "₹" as hand strokes (round pen, a few slightly shifted passes, like
    //    crayon), centred where the "$" was and tilted a little.
    var glyph = new Bitmap(img.Width, img.Height, PixelFormat.Format32bppArgb);
    using (var g = Graphics.FromImage(glyph)) {
      g.Clear(Color.Black);
      g.SmoothingMode = SmoothingMode.AntiAlias;
      float cx = dollar.Left + dollar.Width / 2f, cy = dollar.Top + dollar.Height / 2f;
      float k = size / 160f;
      for (int pass = 0; pass < 3; pass++) {
        var path = new GraphicsPath();
        path.StartFigure(); path.AddLine(-40, -76, 42, -76);
        path.StartFigure(); path.AddLine(-40, -44, 42, -44);
        path.StartFigure(); path.AddBezier(-30, -76, 30, -76, 34, -10, -34, -10); path.AddLine(-34, -10, 36, 80);
        var m = new Matrix();
        m.Translate(cx + (float)(rnd.NextDouble() * 3 - 1.5), cy + (float)(rnd.NextDouble() * 3 - 1.5));
        m.Rotate(angle); m.Scale(k, k);
        path.Transform(m);
        using (var pen = new Pen(Color.FromArgb(pass == 0 ? 255 : 150, 255, 255, 255), (13f + pass * 1.5f) * k)) {
          pen.StartCap = LineCap.Round; pen.EndCap = LineCap.Round; pen.LineJoin = LineJoin.Round;
          g.DrawPath(pen, path);
        }
      }
    }
    for (int y = 0; y < img.Height; y++) for (int x = 0; x < img.Width; x++) {
      int a = glyph.GetPixel(x, y).R; if (a == 0) continue;
      // Crayon: some pixels inside the stroke are lighter, so paper shows through like the outline.
      double t = a / 255.0 * (rnd.NextDouble() < 0.15 ? 0.6 : 1.0);
      var d = img.GetPixel(x, y); var s = ink[rnd.Next(ink.Count)];
      img.SetPixel(x, y, Color.FromArgb(255, (int)(d.R + (s.R - d.R) * t), (int)(d.G + (s.G - d.G) * t), (int)(d.B + (s.B - d.B) * t)));
    }
    img.Save(outPath, ImageFormat.Png);
    return "fillLow=" + fillLow.ToString("0") + " fillMed=" + fillL[fillL.Count / 2].ToString("0") + " inkMed=" + inkL.ToString("0") + " covered=" + covered;
  }
}
"@
[Rup]::Run((Resolve-Path $Src).Path, [IO.Path]::GetFullPath($Out), $Font, $Size, $Angle, $Seed)
