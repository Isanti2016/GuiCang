import { del, get, post } from "@/utils/http";

/** 收藏项（与后端 FavoriteVO 对应）。 */
export interface FavoriteVO {
  path: string;
  name: string;
  createdAt: number;
}

export function addFavorite(path: string): Promise<void> {
  return post<void>("/favorites", { path });
}

export function removeFavorite(path: string): Promise<void> {
  return del<void>("/favorites", { path });
}

export function listFavorites(): Promise<FavoriteVO[]> {
  return get<FavoriteVO[]>("/favorites");
}
