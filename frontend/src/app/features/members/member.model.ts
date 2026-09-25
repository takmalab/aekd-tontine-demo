/** Miroir de MemberResponse (backend). */
export interface Member {
  id: string;
  userId: string;
  email: string;
  fullName: string;
  phone: string | null;
  joinDate: string;
  active: boolean;
  createdAt: string;
}
