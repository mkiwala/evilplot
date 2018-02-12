package com.cibo.evilplot.plot

import com.cibo.evilplot.geometry.{Drawable, Extent}
import com.cibo.evilplot.numeric._
import com.cibo.evilplot.plot.renderers.{
  PathRenderer,
  PlotRenderer,
  SurfaceRenderer
}

object SurfacePlot {
  private[plot] val defaultBoundBuffer: Double = 0.2

  private[plot] case class SurfacePlotRenderer(
    surfaceRenderer: SurfaceRenderer
  ) extends PlotRenderer[Seq[Seq[Point3]]] {
    def render(plot: Plot[Seq[Seq[Point3]]], plotExtent: Extent): Drawable = {
      val xtransformer = plot.xtransform(plot, plotExtent)
      val ytransformer = plot.ytransform(plot, plotExtent)

      plot.data.map { level =>
        val transformedLevel = level.withFilter { p =>
          plot.xbounds.isInBounds(p.x) && plot.ybounds.isInBounds(p.y)
        }.map { p => Point3(xtransformer(p.x), ytransformer(p.y), p.z) }
        surfaceRenderer.render(plotExtent, plot.data, transformedLevel)
      }.group
    }
  }
}

object ContourPlot {
  import SurfacePlot._
  val defaultNumContours: Int = 20
  val defaultGridDimensions: (Int, Int) = (100, 100)
  def apply(
    data: Seq[Point],
    gridDimensions: (Int, Int) = defaultGridDimensions,
    surfaceRenderer: SurfaceRenderer = SurfaceRenderer.densityColorContours(),
    contours: Int = defaultNumContours,
    boundBuffer: Double = defaultBoundBuffer
  ): Plot[Seq[Seq[Point3]]] = {
    require(contours > 0, "Must use at least one contour.")

    val xbounds = Plot.expandBounds(Bounds(data.minBy(_.x).x, data.maxBy(_.x).x), boundBuffer)
    val ybounds = Plot.expandBounds(Bounds(data.minBy(_.y).y, data.maxBy(_.y).y), boundBuffer)

    val gridData = KernelDensityEstimation.densityEstimate2D(data, gridDimensions, Some(xbounds), Some(ybounds))

    val binWidth = gridData.zBounds.range / contours
    val levels = Seq.tabulate(contours - 1) { bin =>
      gridData.zBounds.min + (bin + 1) * binWidth
    }

    val contourPoints = levels.map { l =>
      toPoint3(MarchingSquares.getContoursAt(l, gridData), l)
    }

    Plot[Seq[Seq[Point3]]](
      contourPoints,
      xbounds,
      ybounds,
      SurfacePlotRenderer(surfaceRenderer),
      legendContext = surfaceRenderer.legendContext(contourPoints)
    )
  }

  // MS implementation returns Seq[Segment], bridge to Seq[Point3] to avoid
  // breaking old plots (for now...)
  private def toPoint3(segments: Seq[Segment],
                       level: Double): Vector[Point3] = {
    segments
      .flatMap(s =>
        Vector(Point3(s.a.x, s.a.y, level), Point3(s.b.x, s.b.y, level)))
      .toVector
  }
}