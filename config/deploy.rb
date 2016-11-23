# config valid only for current version of Capistrano
# lock '3.4.0'

set :application, "pricewars-marketplace"
set :repo_url, "git@github.com:hpi-epic/pricewars-marketplace.git"
set :scm, :git

set :pty, true

set :format, :pretty

# Default value for keep_releases is 5
set :keep_releases, 5

# Default branch is :master
# ask :branch, `git rev-parse --abbrev-ref HEAD`.chomp

set :default_env, rvm_bin_path: "~/.rvm/bin/"
# set :bundle_gemfile, "backend/Gemfile"
# set :repo_tree, 'backend'

# Default deploy_to directory is /var/www/my_app_name
set :deploy_to, "/var/www/pricewars-marketplace"

# Default value for :scm is :git
# set :scm, :git
set :rvm_ruby_version, "2.3.1"

# Default value for :format is :pretty
# set :format, :pretty

# Default value for :log_level is :debug
# set :log_level, :debug

# Default value for :pty is false
# set :pty, true

# Default value for :linked_files is []
# set :linked_files, fetch(:linked_files, []).push('config/database.yml', 'config/secrets.yml')

# Default value for linked_dirs is []
# set :linked_dirs, fetch(:linked_dirs, []).push('log', 'tmp/pids', 'tmp/cache', 'tmp/sockets', 'vendor/bundle', 'public/system')

# Default value for default_env is {}
# set :default_env, { path: "/opt/ruby/bin:$PATH" }

# Default value for keep_releases is 5
# set :keep_releases, 5

namespace :deploy do
  task :install_tomcat_war do
    on roles :all do
      within release_path do
        execute "sudo chown -R deployer:www-data /var/www/pricewars-marketplace/"
        execute "sudo mv #{release_path}/src/main/resources/application.deployment.conf  #{release_path}/src/main/resources/application.conf"
        execute "cd #{release_path} && sudo sbt package"
        execute "sudo service tomcat7 stop"
        execute "sudo rm -rf /var/lib/tomcat7/webapps/marketplace"
        execute "sudo mv #{release_path}/target/scala-2.11/marketplace_2.11-1.0.war  /var/lib/tomcat7/webapps/marketplace.war"
        execute "sudo service tomcat7 start"
      end
    end
  end

  after :deploy, "deploy:install_tomcat_war"
end
