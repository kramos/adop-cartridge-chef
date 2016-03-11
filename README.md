# What is Cartridge?

A Cartridge is a set of resources that are loaded into the Platform for a particular project. They may contain anything from a simple reference implementation for a technology to a set of best practice examples for building, deploying, and managing a technology stack that can be used by a project.

This cartridge consists of source code repositories and jenkins jobs.

## Source code repositories

Cartrige loads the source code repositories


* [Sample cookbook - VIM](https://github.com/kramos/vim.git)
* [Scripts used by this cartridge - adop-cartridge-chef-scripts](https://github.com/kramos/adop-cartridge-chef-scripts.git)

## Jenkins Jobs

This cartridge generates the jenkins jobs and pipeline views to -

* Detect cookbook changes 
* Perform sanity checks and static code analysis 
* Run unit tests
* TODO: check for missing cookbook dependencies  
* TODO: check version has been bumped
* TODO: Kitchen converge
* TODO: Put to a Chef server

* TODO: provision a Chef server in ADOP

# License
Please view [license information](LICENSE.md) for the software contained on this image.

## Documentation
Documentation will be captured within this README.md and this repository's Wiki.

## Issues
If you have any problems with or questions about this image, please contact us through a [GitHub issue](https://github.com/Accenture/adop-platform-management/issues).

## Contribute
You are invited to contribute new features, fixes, or updates, large or small; we are always thrilled to receive pull requests, and do our best to process them as fast as we can.

Before you start to code, we recommend discussing your plans through a [GitHub issue](https://github.com/Accenture/adop-platform-management/issues), especially for more ambitious contributions. This gives other contributors a chance to point you in the right direction, give you feedback on your design, and help you find out if someone else is working on the same thing.


