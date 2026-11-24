# THROWAWAY SPIKE (issue #186).
# Automatic signing on a fresh CI machine creates a new development certificate on every run.
# `snapshot` records the certificates that exist before the build; `prune` deletes the
# development certificates that appeared since, so they do not pile up on the account.
# It never touches a certificate that existed before the run, or any distribution certificate.
#
#   ruby certs.rb snapshot <file>
#   ruby certs.rb prune <file>
require "base64"
require "json"
require "net/http"
require "openssl"
require "uri"

def b64(data)
  Base64.urlsafe_encode64(data, padding: false)
end

def token
  key = OpenSSL::PKey.read(File.read(ENV.fetch("ASC_KEY_PATH")))
  now = Time.now.to_i
  header = { alg: "ES256", kid: ENV.fetch("ASC_KEY_ID"), typ: "JWT" }
  payload = { iss: ENV.fetch("ASC_ISSUER_ID"), iat: now, exp: now + 600, aud: "appstoreconnect-v1" }
  input = "#{b64(header.to_json)}.#{b64(payload.to_json)}"
  der = key.sign(OpenSSL::Digest.new("SHA256"), input)
  # JWT wants the raw r||s pair, OpenSSL gives ASN.1.
  raw = OpenSSL::ASN1.decode(der).value.map { |n| n.value.to_s(2).rjust(32, "\x00".b) }.join
  "#{input}.#{b64(raw)}"
end

def call(method, path)
  uri = URI("https://api.appstoreconnect.apple.com#{path}")
  request = (method == :delete ? Net::HTTP::Delete : Net::HTTP::Get).new(uri)
  request["Authorization"] = "Bearer #{token}"
  Net::HTTP.start(uri.host, uri.port, use_ssl: true) { |http| http.request(request) }
end

def certificates
  response = call(:get, "/v1/certificates?limit=200")
  abort "listing certificates failed: HTTP #{response.code}" unless response.code == "200"
  JSON.parse(response.body).fetch("data")
end

mode, file = ARGV
case mode
when "snapshot"
  ids = certificates.map { |c| c["id"] }
  File.write(file, ids.join("\n"))
  puts "#{ids.size} certificates before the build"
when "prune"
  before = File.exist?(file) ? File.read(file).split("\n") : abort("no snapshot, pruning nothing")
  fresh = certificates.reject { |c| before.include?(c["id"]) }
  fresh.each do |c|
    type = c.dig("attributes", "certificateType").to_s
    if type.include?("DEVELOPMENT")
      code = call(:delete, "/v1/certificates/#{c["id"]}").code
      puts "deleted new #{type} certificate: HTTP #{code}"
    else
      puts "kept new #{type} certificate"
    end
  end
  puts "nothing to prune" if fresh.empty?
else
  abort "usage: certs.rb snapshot|prune <file>"
end
