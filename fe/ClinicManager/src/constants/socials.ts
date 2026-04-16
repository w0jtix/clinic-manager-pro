import websiteIcon from "../assets/website_icon.svg";
import instagramIcon from "../assets/instagram_icon.svg";
import facebookIcon from "../assets/facebook_icon.svg";

export interface SocialItem {
  name: string;
  href: string;
  icon: string;
  alt: string;
}

export const SOCIAL_ITEMS: SocialItem[] = [
  { name: "Website", href: "https://example.com/", icon: websiteIcon, alt: "website-icon" },
  { name: "Instagram", href: "https://example.com/", icon: instagramIcon, alt: "instagram-icon" },
  { name: "Facebook", href: "https://example.com/", icon: facebookIcon, alt: "facebook-icon" },
];