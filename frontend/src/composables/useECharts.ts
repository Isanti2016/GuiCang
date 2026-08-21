import * as echarts from "echarts";
import { onBeforeUnmount, onMounted, type Ref } from "vue";

/** ECharts 组合式封装：自动初始化/自适应/销毁。 */
export function useECharts(container: Ref<HTMLElement | null>) {
  let chart: echarts.ECharts | null = null;

  const handleResize = (): void => {
    chart?.resize();
  };

  onMounted(() => {
    if (container.value) {
      chart = echarts.init(container.value);
      window.addEventListener("resize", handleResize);
    }
  });

  onBeforeUnmount(() => {
    window.removeEventListener("resize", handleResize);
    chart?.dispose();
    chart = null;
  });

  function setOption(option: echarts.EChartsOption): void {
    chart?.setOption(option);
  }

  /** 清空画布（用于无数据场景）。 */
  function clear(): void {
    chart?.clear();
  }

  return { setOption, clear };
}
