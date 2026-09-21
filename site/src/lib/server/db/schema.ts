import {
  boolean,
  index,
  integer,
  pgTable,
  primaryKey,
  text,
  timestamp,
  uniqueIndex,
} from "drizzle-orm/pg-core"
import type { AdapterAccountType } from "@auth/core/adapters"

export const users = pgTable("user", {
  id: text("id").primaryKey().$defaultFn(() => crypto.randomUUID()),
  name: text("name"),
  email: text("email").unique(),
  emailVerified: timestamp("emailVerified", { mode: "date" }),
  image: text("image"),
  handle: text("handle").notNull().$defaultFn(() => "user-" + crypto.randomUUID().slice(0, 6)),
  role: text("role").notNull().default("member"),
  minecraftId: text("minecraftId"),
  minecraftName: text("minecraftName"),
  discordId: text("discordId").unique(),
  discordName: text("discordName"),
  bio: text("bio"),
  postCount: integer("postCount").notNull().default(0),
  topicCount: integer("topicCount").notNull().default(0),
  bannedUntil: timestamp("bannedUntil", { mode: "date" }),
  mutedUntil: timestamp("mutedUntil", { mode: "date" }),
  banReason: text("banReason"),
  trusted: boolean("trusted").notNull().default(false),
  createdAt: timestamp("createdAt", { mode: "date" }).notNull().defaultNow(),
})

export const accounts = pgTable(
  "account",
  {
    userId: text("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
    type: text("type").$type<AdapterAccountType>().notNull(),
    provider: text("provider").notNull(),
    providerAccountId: text("providerAccountId").notNull(),
    refresh_token: text("refresh_token"),
    access_token: text("access_token"),
    expires_at: integer("expires_at"),
    token_type: text("token_type"),
    scope: text("scope"),
    id_token: text("id_token"),
    session_state: text("session_state"),
  },
  (account) => [primaryKey({ columns: [account.provider, account.providerAccountId] })],
)

export const sessions = pgTable("session", {
  sessionToken: text("sessionToken").primaryKey(),
  userId: text("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  expires: timestamp("expires", { mode: "date" }).notNull(),
})

export const verificationTokens = pgTable(
  "verificationToken",
  {
    identifier: text("identifier").notNull(),
    token: text("token").notNull(),
    expires: timestamp("expires", { mode: "date" }).notNull(),
  },
  (token) => [primaryKey({ columns: [token.identifier, token.token] })],
)

export const categories = pgTable("category", {
  id: text("id").primaryKey(),
  name: text("name").notNull(),
  blurb: text("blurb").notNull(),
  icon: text("icon").notNull(),
  position: integer("position").notNull().default(0),
  staffOnly: boolean("staffOnly").notNull().default(false),
})

export const topics = pgTable(
  "topic",
  {
    id: text("id").primaryKey().$defaultFn(() => crypto.randomUUID()),
    categoryId: text("categoryId").notNull().references(() => categories.id),
    authorId: text("authorId").notNull().references(() => users.id),
    title: text("title").notNull(),
    slug: text("slug").notNull(),
    pinned: boolean("pinned").notNull().default(false),
    locked: boolean("locked").notNull().default(false),
    solvedPostId: text("solvedPostId"),
    replyCount: integer("replyCount").notNull().default(0),
    viewCount: integer("viewCount").notNull().default(0),
    createdAt: timestamp("createdAt", { mode: "date" }).notNull().defaultNow(),
    lastPostAt: timestamp("lastPostAt", { mode: "date" }).notNull().defaultNow(),
  },
  (topic) => [
    index("topic_category_idx").on(topic.categoryId, topic.lastPostAt),
    uniqueIndex("topic_slug_idx").on(topic.slug),
  ],
)

export const posts = pgTable(
  "post",
  {
    id: text("id").primaryKey().$defaultFn(() => crypto.randomUUID()),
    topicId: text("topicId").notNull().references(() => topics.id, { onDelete: "cascade" }),
    authorId: text("authorId").notNull().references(() => users.id),
    body: text("body").notNull(),
    createdAt: timestamp("createdAt", { mode: "date" }).notNull().defaultNow(),
    editedAt: timestamp("editedAt", { mode: "date" }),
    deletedAt: timestamp("deletedAt", { mode: "date" }),
    hidden: boolean("hidden").notNull().default(false),
    replyToId: text("replyToId"),
  },
  (post) => [index("post_topic_idx").on(post.topicId, post.createdAt)],
)

export const likes = pgTable(
  "like",
  {
    postId: text("postId").notNull().references(() => posts.id, { onDelete: "cascade" }),
    userId: text("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
    createdAt: timestamp("createdAt", { mode: "date" }).notNull().defaultNow(),
  },
  (like) => [primaryKey({ columns: [like.postId, like.userId] })],
)

export const linkCodes = pgTable("linkCode", {
  code: text("code").primaryKey(),
  userId: text("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  expires: timestamp("expires", { mode: "date" }).notNull(),
  attempts: integer("attempts").notNull().default(0),
})

export const rateHits = pgTable("rateHit", {
  key: text("key").primaryKey(),
  count: integer("count").notNull().default(0),
  resetAt: timestamp("resetAt", { mode: "date" }).notNull(),
})

export const badges = pgTable("badge", {
  id: text("id").primaryKey(),
  name: text("name").notNull(),
  blurb: text("blurb").notNull(),
  icon: text("icon").notNull(),
  tone: text("tone").notNull().default("neutral"),
  position: integer("position").notNull().default(0),
})

export const userBadges = pgTable(
  "userBadge",
  {
    userId: text("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
    badgeId: text("badgeId").notNull().references(() => badges.id, { onDelete: "cascade" }),
    grantedAt: timestamp("grantedAt", { mode: "date" }).notNull().defaultNow(),
  },
  (row) => [primaryKey({ columns: [row.userId, row.badgeId] })],
)

export const reads = pgTable(
  "read",
  {
    userId: text("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
    topicId: text("topicId").notNull().references(() => topics.id, { onDelete: "cascade" }),
    readAt: timestamp("readAt", { mode: "date" }).notNull().defaultNow(),
  },
  (row) => [primaryKey({ columns: [row.userId, row.topicId] })],
)

export const watches = pgTable(
  "watch",
  {
    userId: text("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
    topicId: text("topicId").references(() => topics.id, { onDelete: "cascade" }),
    categoryId: text("categoryId").references(() => categories.id, { onDelete: "cascade" }),
    createdAt: timestamp("createdAt", { mode: "date" }).notNull().defaultNow(),
  },
  (row) => [index("watch_user_idx").on(row.userId)],
)

export const reports = pgTable(
  "report",
  {
    id: text("id").primaryKey().$defaultFn(() => crypto.randomUUID()),
    postId: text("postId").notNull().references(() => posts.id, { onDelete: "cascade" }),
    reporterId: text("reporterId").notNull().references(() => users.id, { onDelete: "cascade" }),
    reason: text("reason").notNull(),
    note: text("note"),
    state: text("state").notNull().default("open"),
    handledById: text("handledById").references(() => users.id),
    createdAt: timestamp("createdAt", { mode: "date" }).notNull().defaultNow(),
    handledAt: timestamp("handledAt", { mode: "date" }),
  },
  (row) => [index("report_state_idx").on(row.state, row.createdAt)],
)

export const modLog = pgTable(
  "modLog",
  {
    id: text("id").primaryKey().$defaultFn(() => crypto.randomUUID()),
    staffId: text("staffId").notNull().references(() => users.id),
    action: text("action").notNull(),
    subject: text("subject").notNull(),
    note: text("note"),
    createdAt: timestamp("createdAt", { mode: "date" }).notNull().defaultNow(),
  },
  (row) => [index("modlog_created_idx").on(row.createdAt)],
)

export const uploads = pgTable("upload", {
  id: text("id").primaryKey().$defaultFn(() => crypto.randomUUID()),
  userId: text("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  url: text("url").notNull(),
  kind: text("kind").notNull(),
  bytes: integer("bytes").notNull(),
  createdAt: timestamp("createdAt", { mode: "date" }).notNull().defaultNow(),
})

export const linkPreviews = pgTable("linkPreview", {
  url: text("url").primaryKey(),
  title: text("title"),
  blurb: text("blurb"),
  image: text("image"),
  site: text("site"),
  author: text("author"),
  accent: text("accent"),
  large: boolean("large").notNull().default(false),
  fetchedAt: timestamp("fetchedAt", { mode: "date" }).notNull().defaultNow(),
})
