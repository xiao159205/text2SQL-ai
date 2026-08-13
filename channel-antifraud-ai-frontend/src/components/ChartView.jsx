import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'

/** ECharts 容器组件：负责初始化、更新与自适应 */
export default function ChartView({ option, height = 340 }) {
  const ref = useRef(null)
  const chartRef = useRef(null)

  useEffect(() => {
    if (!ref.current) return
    chartRef.current = echarts.init(ref.current)
    const resize = () => chartRef.current?.resize()
    window.addEventListener('resize', resize)
    return () => {
      window.removeEventListener('resize', resize)
      chartRef.current?.dispose()
      chartRef.current = null
    }
  }, [])

  useEffect(() => {
    if (chartRef.current && option) {
      chartRef.current.setOption(option, true)
    }
  }, [option])

  return <div ref={ref} className="result-chart" style={{ height }} />
}
