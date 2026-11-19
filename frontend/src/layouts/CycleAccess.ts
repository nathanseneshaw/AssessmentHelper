import { createContext, useContext } from "react";

export const CycleReadOnlyContext = createContext(false);

export function useCycleReadOnly() {
  return useContext(CycleReadOnlyContext);
}
