FROM node:24-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

ARG VITE_USER_API_URL=
ARG VITE_ACTIVITY_API_URL=
ARG VITE_RECOMMENDATION_API_URL=
ENV VITE_USER_API_URL=$VITE_USER_API_URL
ENV VITE_ACTIVITY_API_URL=$VITE_ACTIVITY_API_URL
ENV VITE_RECOMMENDATION_API_URL=$VITE_RECOMMENDATION_API_URL

RUN npm run build

FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
