COPY (
  SELECT *
  FROM authentication_events
) TO STDOUT CSV HEADER;
-- psql -f export_auth_csv.sql > auth_events.csv