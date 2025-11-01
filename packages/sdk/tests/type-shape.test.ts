import type { ClientOnly, ServerOnly, Shared } from '../src/index';

type AssertAssignable<T, U extends T> = true;

type ServerBrand = ServerOnly["__brand"];
type ClientBrand = ClientOnly["__brand"];
type SharedBrand = Shared["__brand"];

// These assignments will fail to compile if the expected brands drift.
const serverBrandCheck: AssertAssignable<"server", ServerBrand> = true;
const clientBrandCheck: AssertAssignable<"client", ClientBrand> = true;
const sharedBrandCheck: AssertAssignable<"shared", SharedBrand> = true;

// Ensure shared types remain mutually assignable in shared contexts but not across server/client boundaries.
type _SharedIsNotServer = Shared extends ServerOnly ? never : true;
type _SharedIsNotClient = Shared extends ClientOnly ? never : true;

export default {
  serverBrandCheck,
  clientBrandCheck,
  sharedBrandCheck,
};
